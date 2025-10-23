/*
 * Copyright 2024-2025 the original author or authors.
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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.prompt.PromptConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.constant.Constant.*;
import com.alibaba.cloud.ai.util.StateUtils;

/**
 * CSV分析节点 - 基于CSV数据生成Python分析代码
 * 
 * @author zhangshenghang
 */
@Component
public class CsvAnalyzeNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(CsvAnalyzeNode.class);

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        logger.info("开始CSV数据分析");

        try {
            // 1. 获取用户查询和CSV结构信息
            String userQuery = StateUtils.getStringValue(state, QUERY_REWRITE_NODE_OUTPUT);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> csvSchemas = (List<Map<String, Object>>) state.value(CSV_SCHEMAS).orElse(List.of());
            
            if (csvSchemas.isEmpty()) {
                logger.warn("没有CSV结构信息，跳过分析");
                return Map.of(
                    CSV_ANALYSIS_CODE, "",
                    ANALYSIS_TYPE, "NO_CSV_DATA"
                );
            }

            logger.info("开始分析 {} 个CSV文件的数据", csvSchemas.size());

            // 2. 构建分析提示词
            Map<String, Object> params = new java.util.HashMap<>();
            params.put("user_query", userQuery);
            params.put("csv_schemas", csvSchemas);
            String systemPrompt = PromptConstant.getCsvAnalysisPromptTemplate().render(params);

            // 3. 调用LLM生成Python分析代码
            ChatClient chatClient = chatClientBuilder.build();
            Flux<ChatResponse> analysisFlux = chatClient.prompt()
                .system(systemPrompt)
                .stream()
                .chatResponse();

            // 4. 生成分析代码
            StringBuilder analysisCodeBuilder = new StringBuilder();
            analysisFlux.toStream().forEach(response -> {
                if (response.getResult() != null && response.getResult().getOutput() != null) {
                    analysisCodeBuilder.append(response.getResult().getOutput().getText());
                }
            });
            String analysisCode = analysisCodeBuilder.toString();

            logger.info("CSV分析代码生成完成");

            // 5. 返回分析结果
            Map<String, Object> result = new java.util.HashMap<>();
            result.put(CSV_ANALYSIS_CODE, analysisCode);
            result.put(ANALYSIS_TYPE, "CSV_ANALYSIS");
            return result;

        } catch (Exception e) {
            logger.error("CSV分析失败", e);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put(CSV_ANALYSIS_CODE, "");
            result.put(ANALYSIS_TYPE, "ERROR");
            result.put(ERROR_MESSAGE, "CSV分析失败: " + e.getMessage());
            return result;
        }
    }
}
