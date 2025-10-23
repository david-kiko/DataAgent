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
import com.alibaba.cloud.ai.service.base.BaseSchemaService;
import com.alibaba.cloud.ai.util.ChatResponseUtil;
import com.alibaba.cloud.ai.util.StateUtils;
import com.alibaba.cloud.ai.util.StreamingChatGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.constant.Constant.COLUMN_DOCUMENTS_BY_KEYWORDS_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.constant.Constant.KEYWORD_EXTRACT_NODE_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.SCHEMA_RECALL_NODE_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.CSV_FILE_DOCUMENTS_OUTPUT;
import static com.alibaba.cloud.ai.constant.Constant.CSV_COLUMN_DOCUMENTS_OUTPUT;

/**
 * Schema recall node that retrieves relevant database schema information based on
 * keywords and intent.
 *
 * This node is responsible for: - Recalling relevant tables based on user input -
 * Retrieving column documents based on extracted keywords - Organizing schema information
 * for subsequent processing - Providing streaming feedback during recall process
 *
 * @author zhangshenghang
 */
public class SchemaRecallNode implements NodeAction {

	private static final Logger logger = LoggerFactory.getLogger(SchemaRecallNode.class);

	private final BaseSchemaService baseSchemaService;

	public SchemaRecallNode(BaseSchemaService baseSchemaService) {
		this.baseSchemaService = baseSchemaService;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		logger.info("Entering {} node", this.getClass().getSimpleName());

		String input = StateUtils.getStringValue(state, INPUT_KEY);
		List<String> keywords = StateUtils.getListValue(state, KEYWORD_EXTRACT_NODE_OUTPUT);
		String agentId = StateUtils.getStringValue(state, AGENT_ID);

		// Execute business logic first - recall schema information immediately
		List<Document> tableDocuments;
		List<List<Document>> columnDocumentsByKeywords;
		
		// CSV schema recall
		List<Document> csvFileDocuments;
		List<List<Document>> csvColumnDocumentsByKeywords;

		// If agentId exists, use agent-specific search, otherwise use global search
		if (agentId != null && !agentId.trim().isEmpty()) {
			logger.info("Using agent-specific schema recall for agent: {}", agentId);
			// Database schema recall
			tableDocuments = baseSchemaService.getTableDocumentsForAgent(agentId, input);
			columnDocumentsByKeywords = baseSchemaService.getColumnDocumentsByKeywordsForAgent(agentId, keywords);
			// CSV schema recall
			csvFileDocuments = baseSchemaService.getCsvFileDocumentsForAgent(agentId, input);
			csvColumnDocumentsByKeywords = baseSchemaService.getCsvColumnDocumentsByKeywordsForAgent(agentId, keywords);
		}
		else {
			logger.info("Using global schema recall (no agentId provided)");
			// Database schema recall
			tableDocuments = baseSchemaService.getTableDocuments(input);
			columnDocumentsByKeywords = baseSchemaService.getColumnDocumentsByKeywords(keywords);
			// CSV schema recall
			csvFileDocuments = baseSchemaService.getCsvFileDocuments(input, null);
			csvColumnDocumentsByKeywords = baseSchemaService.getCsvColumnDocumentsByKeywords(keywords, null);
		}

		logger.info(
				"[{}] Schema recall results - table documents count: {}, keyword-related column document groups: {}, csv file documents count: {}, csv column document groups: {}",
				this.getClass().getSimpleName(), tableDocuments.size(), columnDocumentsByKeywords.size(), 
				csvFileDocuments.size(), csvColumnDocumentsByKeywords.size());

		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createStatusResponse("开始召回Schema信息..."));
			emitter.next(ChatResponseUtil.createStatusResponse("数据库表信息召回完成，数量: " + tableDocuments.size()));
			emitter.next(ChatResponseUtil.createStatusResponse("数据库列信息召回完成，数量: " + columnDocumentsByKeywords.size()));
			emitter.next(ChatResponseUtil.createStatusResponse("CSV文件信息召回完成，数量: " + csvFileDocuments.size()));
			emitter.next(ChatResponseUtil.createStatusResponse("CSV列信息召回完成，数量: " + csvColumnDocumentsByKeywords.size()));
			emitter.next(ChatResponseUtil.createStatusResponse("Schema信息召回完成."));
			emitter.complete();
		});

		var generator = StreamingChatGeneratorUtil.createStreamingGeneratorWithMessages(this.getClass(), state,
				currentState -> {
					logger.info("Table document details: {}", tableDocuments);
					logger.info("Keyword-related column document details: {}", columnDocumentsByKeywords);
					logger.info("CSV file document details: {}", csvFileDocuments);
					logger.info("CSV column document details: {}", csvColumnDocumentsByKeywords);
					return Map.of(
						TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocuments,
						COLUMN_DOCUMENTS_BY_KEYWORDS_OUTPUT, columnDocumentsByKeywords,
						CSV_FILE_DOCUMENTS_OUTPUT, csvFileDocuments,
						CSV_COLUMN_DOCUMENTS_OUTPUT, csvColumnDocumentsByKeywords
					);
				}, displayFlux, StreamResponseType.SCHEMA_RECALL);

		// Return the processing result
		return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
	}

}
