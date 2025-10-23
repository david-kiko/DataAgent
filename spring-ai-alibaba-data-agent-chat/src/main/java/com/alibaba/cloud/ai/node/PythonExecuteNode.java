/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.node;

import com.alibaba.cloud.ai.enums.StreamResponseType;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.service.code.CodePoolExecutorService;
import com.alibaba.cloud.ai.util.ChatResponseUtil;
import com.alibaba.cloud.ai.util.StateUtils;
import com.alibaba.cloud.ai.util.StreamingChatGeneratorUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.constant.Constant.PYTHON_EXECUTE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.PYTHON_GENERATE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.PYTHON_IS_SUCCESS;
import static com.alibaba.cloud.ai.constant.Constant.SQL_RESULT_LIST_MEMORY;
import static com.alibaba.cloud.ai.constant.Constant.DATA_SOURCE_TYPE;
import static com.alibaba.cloud.ai.constant.Constant.CSV_SCHEMAS;
import static com.alibaba.cloud.ai.constant.Constant.SESSION_ID;

/**
 * 根据SQL查询结果生成Python代码，并运行Python代码获取运行结果。
 *
 * @author vlsmb
 * @since 2025/7/29
 */
public class PythonExecuteNode extends AbstractPlanBasedNode implements NodeAction {

	private static final Logger log = LoggerFactory.getLogger(PythonExecuteNode.class);

	private final CodePoolExecutorService codePoolExecutor;

	private final ObjectMapper objectMapper;

	public PythonExecuteNode(CodePoolExecutorService codePoolExecutor) {
		super();
		this.codePoolExecutor = codePoolExecutor;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		this.logNodeEntry();

		try {
			// 检查数据源类型
			String dataSourceType = StateUtils.getStringValue(state, DATA_SOURCE_TYPE, "DATABASE_ONLY");
			log.info("PythonExecuteNode - 数据源类型: {}", dataSourceType);

			String pythonCode;
			String inputData;
			
			if ("CSV_ONLY".equals(dataSourceType)) {
				// CSV模式：使用统一的Python代码，数据格式统一
				pythonCode = StateUtils.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> csvSchemas = (List<Map<String, Object>>) state.value(CSV_SCHEMAS).orElse(List.of());
				inputData = convertCsvToStandardFormat(csvSchemas);
				log.info("使用统一Python代码处理CSV数据，CSV文件数量: {}", csvSchemas.size());
			} else {
				// 数据库模式：使用原有的Python代码，数据格式统一
				pythonCode = StateUtils.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
				List<Map<String, String>> sqlResults = StateUtils.getListValue(state, SQL_RESULT_LIST_MEMORY);
				inputData = convertSqlToStandardFormat(sqlResults);
				log.info("使用统一Python代码处理数据库数据，SQL结果数量: {}", sqlResults.size());
			}

			CodePoolExecutorService.TaskRequest taskRequest = new CodePoolExecutorService.TaskRequest(pythonCode,
					inputData, null);

			// Run Python code
			CodePoolExecutorService.TaskResponse taskResponse = this.codePoolExecutor.runTask(taskRequest);
			if (!taskResponse.isSuccess()) {
				String errorMsg = "Python Execute Failed!\nStdOut: " + taskResponse.stdOut() + "\nStdErr: "
						+ taskResponse.stdErr() + "\nExceptionMsg: " + taskResponse.exceptionMsg();
				log.error(errorMsg);
				throw new RuntimeException(errorMsg);
			}

			// Python输出的JSON字符串可能有Unicode转义形式，需要解析回汉字
			String stdout = taskResponse.stdOut();
			try {
				Object value = objectMapper.readValue(stdout, Object.class);
				stdout = objectMapper.writeValueAsString(value);
			}
			catch (Exception e) {
				stdout = taskResponse.stdOut();
			}
			
			// 处理图片文件上传
			String finalStdout = processChartFiles(stdout, state);

			log.info("Python Execute Success! StdOut: {}", finalStdout);

			// Create display flux for user experience only
			Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createStatusResponse("开始执行Python代码..."));
				emitter.next(ChatResponseUtil.createStatusResponse("标准输出：\n```"));
				emitter.next(ChatResponseUtil.createStatusResponse(finalStdout));
				emitter.next(ChatResponseUtil.createStatusResponse("\n```"));
				emitter.next(ChatResponseUtil.createStatusResponse("Python代码执行成功！"));
				emitter.complete();
			});

			// Create generator using utility class, returning pre-computed business logic
			// result
			var generator = StreamingChatGeneratorUtil.createStreamingGeneratorWithMessages(this.getClass(), state,
					v -> Map.of(PYTHON_EXECUTE_NODE_OUTPUT, finalStdout, PYTHON_IS_SUCCESS, true), displayFlux,
					StreamResponseType.PYTHON_EXECUTE);

			return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
		}
		catch (Exception e) {
			String errorMessage = e.getMessage();
			log.error("Python Execute Exception: {}", errorMessage);

			// Prepare error result
			Map<String, Object> errorResult = Map.of(PYTHON_EXECUTE_NODE_OUTPUT, errorMessage, PYTHON_IS_SUCCESS,
					false);

			// Create error display flux
			Flux<ChatResponse> errorDisplayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createCustomStatusResponse("开始执行Python代码..."));
				emitter.next(ChatResponseUtil.createCustomStatusResponse("Python代码执行失败: " + errorMessage));
				emitter.complete();
			});

			// Create error generator using utility class
			var generator = StreamingChatGeneratorUtil.createStreamingGeneratorWithMessages(this.getClass(), state,
					v -> errorResult, errorDisplayFlux, StreamResponseType.PYTHON_EXECUTE);

			return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
		}
	}

	/**
	 * 将CSV数据转换为统一格式
	 */
	private String convertCsvToStandardFormat(List<Map<String, Object>> csvSchemas) throws Exception {
		Map<String, Object> standardFormat = new HashMap<>();
		
		// 提取数据行
		List<Map<String, Object>> data = new ArrayList<>();
		for (Map<String, Object> schema : csvSchemas) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rows = (List<Map<String, Object>>) schema.get("data");
			if (rows != null) {
				data.addAll(rows);
			}
		}
		
		// 构建元数据
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("source", "csv");
		metadata.put("row_count", data.size());
		if (!csvSchemas.isEmpty()) {
			@SuppressWarnings("unchecked")
			List<String> columns = (List<String>) csvSchemas.get(0).get("columns");
			metadata.put("columns", columns);
		}
		
		standardFormat.put("data", data);
		standardFormat.put("metadata", metadata);
		
		return objectMapper.writeValueAsString(standardFormat);
	}

	/**
	 * 将SQL结果转换为统一格式
	 */
	private String convertSqlToStandardFormat(List<Map<String, String>> sqlResults) throws Exception {
		Map<String, Object> standardFormat = new HashMap<>();
		
		// 转换数据类型
		List<Map<String, Object>> data = new ArrayList<>();
		for (Map<String, String> row : sqlResults) {
			Map<String, Object> convertedRow = new HashMap<>();
			for (Map.Entry<String, String> entry : row.entrySet()) {
				convertedRow.put(entry.getKey(), entry.getValue());
			}
			data.add(convertedRow);
		}
		
		// 构建元数据
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("source", "database");
		metadata.put("row_count", data.size());
		if (!sqlResults.isEmpty()) {
			metadata.put("columns", sqlResults.get(0).keySet());
		}
		
		standardFormat.put("data", data);
		standardFormat.put("metadata", metadata);
		
		return objectMapper.writeValueAsString(standardFormat);
	}

	/**
	 * 处理Python生成的图片文件，上传到S3并更新结果中的图片路径
	 */
	private String processChartFiles(String stdout, OverAllState state) throws Exception {
		try {
			// 解析Python输出结果
			@SuppressWarnings("unchecked")
			Map<String, Object> result = objectMapper.readValue(stdout, Map.class);
			
			@SuppressWarnings("unchecked")
			List<String> chartPaths = (List<String>) result.get("charts");
			
			if (chartPaths != null && !chartPaths.isEmpty()) {
				log.info("发现 {} 个图片文件需要上传", chartPaths.size());
				
				List<String> s3Urls = new ArrayList<>();
				for (String chartPath : chartPaths) {
					try {
						// 这里需要实现图片上传到S3的逻辑
						// 暂时使用占位符，实际实现需要调用S3FileUploadService
						String s3Url = uploadChartToS3(chartPath, state);
						s3Urls.add(s3Url);
						log.info("图片上传成功: {} -> {}", chartPath, s3Url);
					} catch (Exception e) {
						log.error("图片上传失败: {}", chartPath, e);
						// 上传失败时保留原路径
						s3Urls.add(chartPath);
					}
				}
				
				// 更新结果中的图片路径为S3 URL
				result.put("charts", s3Urls);
				result.put("chart_urls", s3Urls); // 添加chart_urls字段供前端使用
			}
			
			return objectMapper.writeValueAsString(result);
		} catch (Exception e) {
			log.error("处理图片文件失败", e);
			// 如果处理失败，返回原始输出
			return stdout;
		}
	}

	/**
	 * 上传图片文件到S3
	 * TODO: 需要注入S3FileUploadService并实现具体上传逻辑
	 */
	private String uploadChartToS3(String chartPath, OverAllState state) throws Exception {
		// 这里需要实现具体的S3上传逻辑
		// 暂时返回占位符URL
		String sessionId = StateUtils.getStringValue(state, SESSION_ID, "default");
		String fileName = chartPath.substring(chartPath.lastIndexOf("/") + 1);
		return "https://s3.example.com/charts/" + sessionId + "/" + fileName;
	}

}
