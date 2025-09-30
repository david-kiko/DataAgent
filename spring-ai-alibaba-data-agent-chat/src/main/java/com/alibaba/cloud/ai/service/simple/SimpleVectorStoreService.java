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
package com.alibaba.cloud.ai.service.simple;

import com.alibaba.cloud.ai.connector.accessor.Accessor;
import com.alibaba.cloud.ai.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.connector.bo.ColumnInfoBO;
import com.alibaba.cloud.ai.connector.bo.DbQueryParameter;
import com.alibaba.cloud.ai.connector.bo.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;
import com.alibaba.cloud.ai.connector.config.DbConfig;
import com.alibaba.cloud.ai.request.DeleteRequest;
import com.alibaba.cloud.ai.request.SchemaInitRequest;
import com.alibaba.cloud.ai.request.SearchRequest;
import com.alibaba.cloud.ai.service.base.BaseVectorStoreService;
import com.alibaba.cloud.ai.service.TableRelationService;
import com.alibaba.cloud.ai.service.DatasourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Primary
public class SimpleVectorStoreService extends BaseVectorStoreService {

	private static final Logger log = LoggerFactory.getLogger(SimpleVectorStoreService.class);

	private final SimpleVectorStore vectorStore; // Keep original global storage for
													// backward compatibility

	private final AgentVectorStoreManager agentVectorStoreManager; // New agent vector
																	// storage manager

	private final ObjectMapper objectMapper;

	private final Accessor dbAccessor;

	private final DbConfig dbConfig;

	private final EmbeddingModel embeddingModel;

	private final TableRelationService tableRelationService;

	private final DatasourceService datasourceService;

	@Autowired
	public SimpleVectorStoreService(EmbeddingModel embeddingModel, ObjectMapper objectMapper,
			AccessorFactory accessorFactory, DbConfig dbConfig, AgentVectorStoreManager agentVectorStoreManager,
			TableRelationService tableRelationService, DatasourceService datasourceService) {
		log.info("Initializing SimpleVectorStoreService with EmbeddingModel: {}",
				embeddingModel.getClass().getSimpleName());
		this.objectMapper = objectMapper;
		this.dbAccessor = accessorFactory.getAccessorByDbConfig(dbConfig);
		this.dbConfig = dbConfig;
		this.embeddingModel = embeddingModel;
		this.agentVectorStoreManager = agentVectorStoreManager;
		this.tableRelationService = tableRelationService;
		this.datasourceService = datasourceService;
		
		// 注意：SimpleVectorStore 默认使用内存存储，重启后数据会丢失
		// 如需持久化，建议使用其他向量数据库如 Chroma、Milvus 等
		this.vectorStore = SimpleVectorStore.builder(embeddingModel).build(); // Keep original implementation
		log.info("SimpleVectorStoreService initialized successfully with AgentVectorStoreManager");
	}

	@Override
	protected EmbeddingModel getEmbeddingModel() {
		return embeddingModel;
	}

	/**
	 * Initialize database schema to vector store
	 * @param schemaInitRequest schema initialization request
	 * @throws Exception if an error occurs
	 */
	@Override
	public Boolean schema(SchemaInitRequest schemaInitRequest) throws Exception {
		log.info("Starting schema initialization for database: {}, schema: {}, tables: {}",
				schemaInitRequest.getDbConfig().getUrl(), schemaInitRequest.getDbConfig().getSchema(),
				schemaInitRequest.getTables());

		DbConfig dbConfig = schemaInitRequest.getDbConfig();
		DbQueryParameter dqp = DbQueryParameter.from(dbConfig)
			.setSchema(dbConfig.getSchema())
			.setTables(schemaInitRequest.getTables());

		// Clean up old schema data
		DeleteRequest deleteRequest = new DeleteRequest();
		deleteRequest.setVectorType("column");
		deleteDocuments(deleteRequest);
		deleteRequest.setVectorType("table");
		deleteDocuments(deleteRequest);

		// 注释掉原来的数据库元数据获取外键关系的方式
		// log.debug("Fetching foreign keys from database");
		// List<ForeignKeyInfoBO> foreignKeyInfoBOS = dbAccessor.showForeignKeys(dbConfig, dqp);
		// log.debug("Found {} foreign keys", foreignKeyInfoBOS.size());
		
		// 新的外键获取逻辑：从自定义表关联关系表获取
		log.info("Fetching foreign keys from custom table_relation table");
		// 对于全局schema方法，暂时使用null作为数据源ID（使用通用关联关系）
		List<ForeignKeyInfoBO> foreignKeyInfoBOS = getForeignKeysFromCustomTable(dbConfig, null);
		log.info("Found {} foreign keys from custom table_relation table", foreignKeyInfoBOS.size());
		
		Map<String, List<String>> foreignKeyMap = buildForeignKeyMap(foreignKeyInfoBOS);

		// 获取表信息
		log.info("Fetching tables from database");
		List<TableInfoBO> tableInfoBOS = dbAccessor.fetchTables(dbConfig, dqp);
		log.info("Found {} tables to process", tableInfoBOS.size());
		
		// 批量获取所有表的字段信息和样本数据
		Map<String, List<ColumnInfoBO>> tableColumnsMap = new HashMap<>();
		Map<String, Map<String, List<String>>> tableColumnSamplesMap = new HashMap<>();
		
		log.info("Batch fetching columns and samples for all tables");
		for (TableInfoBO tableInfoBO : tableInfoBOS) {
			String tableName = tableInfoBO.getName();
			log.info("Processing table: {}", tableName);
			
			// 获取字段信息
			DbQueryParameter columnParam = new DbQueryParameter();
			columnParam.setSchema(dqp.getSchema());
			columnParam.setTable(tableName);
			List<ColumnInfoBO> columnInfoBOS = dbAccessor.showColumns(dbConfig, columnParam);
			tableColumnsMap.put(tableName, columnInfoBOS);
			
			// 批量获取样本数据
			List<String> columnNames = columnInfoBOS.stream()
				.map(ColumnInfoBO::getName)
				.collect(Collectors.toList());
			Map<String, List<String>> columnSamplesMap = new HashMap<>();
			for (String columnName : columnNames) {
				DbQueryParameter sampleParam = new DbQueryParameter();
				sampleParam.setSchema(dqp.getSchema());
				sampleParam.setTable(tableName);
				sampleParam.setColumn(columnName);
				List<String> samples = dbAccessor.sampleColumn(dbConfig, sampleParam);
				
				// 处理样本数据
				List<String> processedSamples = Optional.ofNullable(samples)
					.orElse(new ArrayList<>())
					.stream()
					.filter(Objects::nonNull)
					.distinct()
					.limit(3)
					.filter(s -> s.length() <= 100)
					.collect(Collectors.toList());
				columnSamplesMap.put(columnName, processedSamples);
			}
			tableColumnSamplesMap.put(tableName, columnSamplesMap);
		}

		// 使用缓存的数据生成向量化文档
		log.info("Converting columns to documents using cached data");
		List<Document> columnDocuments = tableInfoBOS.stream().flatMap(table -> {
			String tableName = table.getName();
			List<ColumnInfoBO> columns = tableColumnsMap.get(tableName);
			Map<String, List<String>> columnSamples = tableColumnSamplesMap.get(tableName);
			
			return columns.stream().map(column -> {
				List<String> samples = columnSamples.get(column.getName());
				return convertToDocumentWithSamples(table, column, samples);
			});
		}).collect(Collectors.toList());

		log.info("Adding {} column documents to vector store", columnDocuments.size());
		vectorStore.add(columnDocuments);

		log.debug("Converting tables to documents");
		List<Document> tableDocuments = tableInfoBOS.stream()
			.map(this::convertTableToDocument)
			.collect(Collectors.toList());

		log.info("Adding {} table documents to vector store", tableDocuments.size());
		vectorStore.add(tableDocuments);

		log.info("Schema initialization completed successfully. Total documents added: {}",
				columnDocuments.size() + tableDocuments.size());
		return true;
	}


	public Document convertToDocument(TableInfoBO tableInfoBO, ColumnInfoBO columnInfoBO) {
		log.debug("Converting column to document: table={}, column={}", tableInfoBO.getName(), columnInfoBO.getName());

		String text = Optional.ofNullable(columnInfoBO.getDescription()).orElse(columnInfoBO.getName());
		String id = tableInfoBO.getName() + "." + columnInfoBO.getName();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("id", id);
		metadata.put("name", columnInfoBO.getName());
		metadata.put("tableName", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(columnInfoBO.getDescription()).orElse(""));
		metadata.put("type", columnInfoBO.getType());
		metadata.put("primary", columnInfoBO.isPrimary());
		metadata.put("notnull", columnInfoBO.isNotnull());
		metadata.put("vectorType", "column");
		if (columnInfoBO.getSamples() != null) {
			metadata.put("samples", columnInfoBO.getSamples());
		}
		// Multi-table duplicate field data will be deduplicated, using table name + field
		// name as unique identifier
		Document document = new Document(id, text, metadata);
		log.debug("Created column document with ID: {}", id);
		return document;
	}

	public Document convertTableToDocument(TableInfoBO tableInfoBO) {
		log.debug("Converting table to document: {}", tableInfoBO.getName());

		String text = Optional.ofNullable(tableInfoBO.getDescription()).orElse(tableInfoBO.getName());
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("schema", Optional.ofNullable(tableInfoBO.getSchema()).orElse(""));
		metadata.put("name", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(tableInfoBO.getDescription()).orElse(""));
		metadata.put("foreignKey", Optional.ofNullable(tableInfoBO.getForeignKey()).orElse(""));
		metadata.put("primaryKey", Optional.ofNullable(tableInfoBO.getPrimaryKeys()).orElse(new ArrayList<>()));
		metadata.put("vectorType", "table");
		Document document = new Document(tableInfoBO.getName(), text, metadata);
		log.debug("Created table document with ID: {}", tableInfoBO.getName());
		return document;
	}

	private Map<String, List<String>> buildForeignKeyMap(List<ForeignKeyInfoBO> foreignKeyInfoBOS) {
		Map<String, List<String>> foreignKeyMap = new HashMap<>();
		for (ForeignKeyInfoBO fk : foreignKeyInfoBOS) {
			String key = fk.getTable() + "." + fk.getColumn() + "=" + fk.getReferencedTable() + "."
					+ fk.getReferencedColumn();

			foreignKeyMap.computeIfAbsent(fk.getTable(), k -> new ArrayList<>()).add(key);
			foreignKeyMap.computeIfAbsent(fk.getReferencedTable(), k -> new ArrayList<>()).add(key);
		}
		return foreignKeyMap;
	}

	/**
	 * Delete vector data with specified conditions
	 * @param deleteRequest delete request
	 * @return whether deletion succeeded
	 */
	public Boolean deleteDocuments(DeleteRequest deleteRequest) throws Exception {
		log.info("Starting delete operation with request: id={}, vectorType={}", deleteRequest.getId(),
				deleteRequest.getVectorType());

		try {
			if (deleteRequest.getId() != null && !deleteRequest.getId().isEmpty()) {
				log.debug("Deleting documents by ID: {}", deleteRequest.getId());
				vectorStore.delete(Arrays.asList(deleteRequest.getId()));
				log.info("Successfully deleted documents by ID");
			}
			else if (deleteRequest.getVectorType() != null && !deleteRequest.getVectorType().isEmpty()) {
				log.debug("Deleting documents by vectorType: {}", deleteRequest.getVectorType());
				FilterExpressionBuilder b = new FilterExpressionBuilder();
				Filter.Expression expression = b.eq("vectorType", deleteRequest.getVectorType()).build();
				List<Document> documents = vectorStore
					.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
						.topK(Integer.MAX_VALUE)
						.filterExpression(expression)
						.build());
				if (documents != null && !documents.isEmpty()) {
					log.info("Found {} documents to delete with vectorType: {}", documents.size(),
							deleteRequest.getVectorType());
					vectorStore.delete(documents.stream().map(Document::getId).toList());
					log.info("Successfully deleted {} documents", documents.size());
				}
				else {
					log.info("No documents found to delete with vectorType: {}", deleteRequest.getVectorType());
				}
			}
			else {
				log.warn("Invalid delete request: either id or vectorType must be specified");
				throw new IllegalArgumentException("Either id or vectorType must be specified.");
			}
			return true;
		}
		catch (Exception e) {
			log.error("Failed to delete documents: {}", e.getMessage(), e);
			throw new Exception("Failed to delete collection data by filterExpression: " + e.getMessage(), e);
		}
	}

	/**
	 * Search interface with default filter
	 */
	@Override
	public List<Document> searchWithVectorType(SearchRequest searchRequestDTO) {
		log.debug("Searching with vectorType: {}, query: {}, topK: {}", searchRequestDTO.getVectorType(),
				searchRequestDTO.getQuery(), searchRequestDTO.getTopK());

		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression expression = b.eq("vectorType", searchRequestDTO.getVectorType()).build();

		List<Document> results = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
			.query(searchRequestDTO.getQuery())
			.topK(searchRequestDTO.getTopK())
			.filterExpression(expression)
			.build());

		if (results == null) {
			results = new ArrayList<>();
		}

		log.info("Search completed. Found {} documents for vectorType: {}", results.size(),
				searchRequestDTO.getVectorType());
		return results;
	}

	/**
	 * Search interface with custom filter
	 */
	@Override
	public List<Document> searchWithFilter(SearchRequest searchRequestDTO) {
		log.debug("Searching with custom filter: vectorType={}, query={}, topK={}", searchRequestDTO.getVectorType(),
				searchRequestDTO.getQuery(), searchRequestDTO.getTopK());

		// Need to parse filterFormatted field according to actual situation here, convert
		// to FilterExpressionBuilder expression
		// Simplified implementation, for demonstration only
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression expression = b.eq("vectorType", searchRequestDTO.getVectorType()).build();

		List<Document> results = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
			.query(searchRequestDTO.getQuery())
			.topK(searchRequestDTO.getTopK())
			.filterExpression(expression)
			.build());

		if (results == null) {
			results = new ArrayList<>();
		}

		log.info("Search with filter completed. Found {} documents", results.size());
		return results;
	}

	@Override
	public List<Document> searchTableByNameAndVectorType(SearchRequest searchRequestDTO) {
		log.debug("Searching table by name and vectorType: name={}, vectorType={}, topK={}", searchRequestDTO.getName(),
				searchRequestDTO.getVectorType(), searchRequestDTO.getTopK());

		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression expression = b
			.and(b.eq("vectorType", searchRequestDTO.getVectorType()), b.eq("id", searchRequestDTO.getName()))
			.build();

		List<Document> results = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
			.topK(searchRequestDTO.getTopK())
			.filterExpression(expression)
			.build());

		if (results == null) {
			results = new ArrayList<>();
		}

		log.info("Search by name completed. Found {} documents for name: {}", results.size(),
				searchRequestDTO.getName());
		return results;
	}

	// ==================== 智能体相关的新方法 ====================

	/**
	 * Initialize database schema to vector store for specified agent
	 * @param agentId agent ID
	 * @param schemaInitRequest schema initialization request
	 * @param datasourceId data source ID (optional, for custom table relations)
	 * @throws Exception if an error occurs
	 */
	public Boolean schemaForAgent(String agentId, SchemaInitRequest schemaInitRequest, Integer datasourceId) throws Exception {
		log.info("Starting schema initialization for agent: {}, database: {}, schema: {}, tables: {}", agentId,
				schemaInitRequest.getDbConfig().getUrl(), schemaInitRequest.getDbConfig().getSchema(),
				schemaInitRequest.getTables());

		DbConfig dbConfig = schemaInitRequest.getDbConfig();
		DbQueryParameter dqp = DbQueryParameter.from(dbConfig)
			.setSchema(dbConfig.getSchema())
			.setTables(schemaInitRequest.getTables());

		// Clean up agent's old data
		agentVectorStoreManager.deleteDocumentsByType(agentId, "column");
		agentVectorStoreManager.deleteDocumentsByType(agentId, "table");

		// 注释掉原来的数据库元数据获取外键关系的方式
		// log.debug("Fetching foreign keys from database for agent: {}", agentId);
		// List<ForeignKeyInfoBO> foreignKeyInfoBOS = dbAccessor.showForeignKeys(dbConfig, dqp);
		// log.debug("Found {} foreign keys for agent: {}", foreignKeyInfoBOS.size(), agentId);
		
		// 新的外键获取逻辑：从自定义表关联关系表获取
		log.info("Fetching foreign keys from custom table_relation table for agent: {}", agentId);
		log.info("Using provided datasource ID: {} for database: {}", datasourceId, dbConfig.getUrl());
		List<ForeignKeyInfoBO> foreignKeyInfoBOS = getForeignKeysFromCustomTable(dbConfig, datasourceId);
		log.info("Found {} foreign keys from custom table_relation table for agent: {}", foreignKeyInfoBOS.size(), agentId);
		
		Map<String, List<String>> foreignKeyMap = buildForeignKeyMap(foreignKeyInfoBOS);

		// 获取表信息
		log.info("Fetching tables from database for agent: {}", agentId);
		List<TableInfoBO> tableInfoBOS = dbAccessor.fetchTables(dbConfig, dqp);
		log.info("Found {} tables to process for agent: {}", tableInfoBOS.size(), agentId);
		
		// 批量获取所有表的字段信息和样本数据
		Map<String, List<ColumnInfoBO>> tableColumnsMap = new HashMap<>();
		Map<String, Map<String, List<String>>> tableColumnSamplesMap = new HashMap<>();
		
		log.info("Batch fetching columns and samples for all tables for agent: {}", agentId);
		for (TableInfoBO tableInfoBO : tableInfoBOS) {
			String tableName = tableInfoBO.getName();
			log.info("Processing table: {} for agent: {}", tableName, agentId);
			
			// 获取字段信息
			DbQueryParameter columnParam = new DbQueryParameter();
			columnParam.setSchema(dqp.getSchema());
			columnParam.setTable(tableName);
			List<ColumnInfoBO> columnInfoBOS = dbAccessor.showColumns(dbConfig, columnParam);
			tableColumnsMap.put(tableName, columnInfoBOS);
			
			// 批量获取样本数据
			List<String> columnNames = columnInfoBOS.stream()
				.map(ColumnInfoBO::getName)
				.collect(Collectors.toList());
			Map<String, List<String>> columnSamplesMap = new HashMap<>();
			for (String columnName : columnNames) {
				DbQueryParameter sampleParam = new DbQueryParameter();
				sampleParam.setSchema(dqp.getSchema());
				sampleParam.setTable(tableName);
				sampleParam.setColumn(columnName);
				List<String> samples = dbAccessor.sampleColumn(dbConfig, sampleParam);
				
				// 处理样本数据
				List<String> processedSamples = Optional.ofNullable(samples)
					.orElse(new ArrayList<>())
					.stream()
					.filter(Objects::nonNull)
					.distinct()
					.limit(3)
					.filter(s -> s.length() <= 100)
					.collect(Collectors.toList());
				columnSamplesMap.put(columnName, processedSamples);
			}
			tableColumnSamplesMap.put(tableName, columnSamplesMap);
			
			// 设置表的主键和外键信息
			List<ColumnInfoBO> targetPrimaryList = columnInfoBOS.stream()
				.filter(ColumnInfoBO::isPrimary)
				.collect(Collectors.toList());
			if (CollectionUtils.isNotEmpty(targetPrimaryList)) {
				List<String> primaryKeyNames = targetPrimaryList.stream()
					.map(ColumnInfoBO::getName)
					.collect(Collectors.toList());
				tableInfoBO.setPrimaryKeys(primaryKeyNames);
			} else {
				tableInfoBO.setPrimaryKeys(new ArrayList<>());
			}
			tableInfoBO.setForeignKey(String.join("、", foreignKeyMap.getOrDefault(tableName, new ArrayList<>())));
		}

		// 使用缓存的数据生成向量化文档
		log.info("Converting columns to documents using cached data for agent: {}", agentId);
		List<Document> columnDocuments = tableInfoBOS.stream().flatMap(table -> {
			String tableName = table.getName();
			List<ColumnInfoBO> columns = tableColumnsMap.get(tableName);
			Map<String, List<String>> columnSamples = tableColumnSamplesMap.get(tableName);
			
			return columns.stream().map(column -> {
				List<String> samples = columnSamples.get(column.getName());
				return convertToDocumentForAgentWithSamples(agentId, table, column, samples);
			});
		}).collect(Collectors.toList());

		log.info("Adding {} column documents to vector store for agent: {}", columnDocuments.size(), agentId);
		agentVectorStoreManager.addDocuments(agentId, columnDocuments);

		log.debug("Converting tables to documents for agent: {}", agentId);
		List<Document> tableDocuments = tableInfoBOS.stream()
			.map(table -> convertTableToDocumentForAgent(agentId, table))
			.collect(Collectors.toList());

		log.info("Adding {} table documents to vector store for agent: {}", tableDocuments.size(), agentId);
		agentVectorStoreManager.addDocuments(agentId, tableDocuments);

		log.info("Schema initialization completed successfully for agent: {}. Total documents added: {}", agentId,
				columnDocuments.size() + tableDocuments.size());
		return true;
	}

	/**
	 * 优化版本：使用预获取的样本数据生成文档
	 */
	private Document convertToDocumentWithSamples(TableInfoBO tableInfoBO, ColumnInfoBO columnInfoBO, List<String> samples) {
		log.debug("Converting column to document with samples: table={}, column={}", tableInfoBO.getName(), columnInfoBO.getName());

		String text = Optional.ofNullable(columnInfoBO.getDescription()).orElse(columnInfoBO.getName());
		String id = tableInfoBO.getName() + "." + columnInfoBO.getName();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("id", id);
		metadata.put("name", columnInfoBO.getName());
		metadata.put("tableName", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(columnInfoBO.getDescription()).orElse(""));
		metadata.put("type", columnInfoBO.getType());
		metadata.put("primary", columnInfoBO.isPrimary());
		metadata.put("notnull", columnInfoBO.isNotnull());
		metadata.put("vectorType", "column");
		metadata.put("samples", samples);

		// 构建包含样本数据的文本内容
		StringBuilder contentBuilder = new StringBuilder(text);
		if (samples != null && !samples.isEmpty()) {
			contentBuilder.append(" 样本数据: ").append(String.join(", ", samples));
		}

		return new Document(contentBuilder.toString(), metadata);
	}

	/**
	 * 优化版本：使用预获取的样本数据生成智能体文档
	 */
	private Document convertToDocumentForAgentWithSamples(String agentId, TableInfoBO tableInfoBO, ColumnInfoBO columnInfoBO, List<String> samples) {
		log.debug("Converting column to document for agent with samples: {}, table={}, column={}", agentId, tableInfoBO.getName(), columnInfoBO.getName());

		String text = Optional.ofNullable(columnInfoBO.getDescription()).orElse(columnInfoBO.getName());
		String id = agentId + ":" + tableInfoBO.getName() + "." + columnInfoBO.getName();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("id", id);
		metadata.put("agentId", agentId);
		metadata.put("name", columnInfoBO.getName());
		metadata.put("tableName", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(columnInfoBO.getDescription()).orElse(""));
		metadata.put("type", columnInfoBO.getType());
		metadata.put("primary", columnInfoBO.isPrimary());
		metadata.put("notnull", columnInfoBO.isNotnull());
		metadata.put("vectorType", "column");
		metadata.put("samples", samples);

		// 构建包含样本数据的文本内容
		StringBuilder contentBuilder = new StringBuilder(text);
		if (samples != null && !samples.isEmpty()) {
			contentBuilder.append(" 样本数据: ").append(String.join(", ", samples));
		}

		return new Document(contentBuilder.toString(), metadata);
	}

	/**
	 * Convert column information to documents for agent
	 */
	private Document convertToDocumentForAgent(String agentId, TableInfoBO tableInfoBO, ColumnInfoBO columnInfoBO) {
		log.debug("Converting column to document for agent: {}, table={}, column={}", agentId, tableInfoBO.getName(),
				columnInfoBO.getName());

		String text = Optional.ofNullable(columnInfoBO.getDescription()).orElse(columnInfoBO.getName());
		String id = agentId + ":" + tableInfoBO.getName() + "." + columnInfoBO.getName();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("id", id);
		metadata.put("agentId", agentId);
		metadata.put("name", columnInfoBO.getName());
		metadata.put("tableName", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(columnInfoBO.getDescription()).orElse(""));
		metadata.put("type", columnInfoBO.getType());
		metadata.put("primary", columnInfoBO.isPrimary());
		metadata.put("notnull", columnInfoBO.isNotnull());
		metadata.put("vectorType", "column");
		if (columnInfoBO.getSamples() != null) {
			metadata.put("samples", columnInfoBO.getSamples());
		}

		Document document = new Document(id, text, metadata);
		log.debug("Created column document with ID: {} for agent: {}", id, agentId);
		return document;
	}

	/**
	 * Convert table information to documents for agent
	 */
	private Document convertTableToDocumentForAgent(String agentId, TableInfoBO tableInfoBO) {
		log.debug("Converting table to document for agent: {}, table: {}", agentId, tableInfoBO.getName());

		String text = Optional.ofNullable(tableInfoBO.getDescription()).orElse(tableInfoBO.getName());
		String id = agentId + ":" + tableInfoBO.getName();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("agentId", agentId);
		metadata.put("schema", Optional.ofNullable(tableInfoBO.getSchema()).orElse(""));
		metadata.put("name", tableInfoBO.getName());
		metadata.put("description", Optional.ofNullable(tableInfoBO.getDescription()).orElse(""));
		metadata.put("foreignKey", Optional.ofNullable(tableInfoBO.getForeignKey()).orElse(""));
		metadata.put("primaryKey", Optional.ofNullable(tableInfoBO.getPrimaryKeys()).orElse(new ArrayList<>()));
		metadata.put("vectorType", "table");

		Document document = new Document(id, text, metadata);
		log.debug("Created table document with ID: {} for agent: {}", id, agentId);
		return document;
	}

	/**
	 * Search vector data for specified agent
	 */
	public List<Document> searchWithVectorTypeForAgent(String agentId, SearchRequest searchRequestDTO) {
		log.debug("Searching for agent: {}, vectorType: {}, query: {}, topK: {}", agentId,
				searchRequestDTO.getVectorType(), searchRequestDTO.getQuery(), searchRequestDTO.getTopK());

		List<Document> results = agentVectorStoreManager.similaritySearchWithFilter(agentId,
				searchRequestDTO.getQuery(), searchRequestDTO.getTopK(), searchRequestDTO.getVectorType());

		log.info("Search completed for agent: {}. Found {} documents for vectorType: {}", agentId, results.size(),
				searchRequestDTO.getVectorType());
		return results;
	}

	/**
	 * Delete vector data for specified agent
	 */
	public Boolean deleteDocumentsForAgent(String agentId, DeleteRequest deleteRequest) throws Exception {
		log.info("Starting delete operation for agent: {}, id={}, vectorType={}", agentId, deleteRequest.getId(),
				deleteRequest.getVectorType());

		try {
			if (deleteRequest.getId() != null && !deleteRequest.getId().isEmpty()) {
				log.debug("Deleting documents by ID for agent: {}, ID: {}", agentId, deleteRequest.getId());
				agentVectorStoreManager.deleteDocuments(agentId, Arrays.asList(deleteRequest.getId()));
				log.info("Successfully deleted documents by ID for agent: {}", agentId);
			}
			else if (deleteRequest.getVectorType() != null && !deleteRequest.getVectorType().isEmpty()) {
				log.debug("Deleting documents by vectorType for agent: {}, vectorType: {}", agentId,
						deleteRequest.getVectorType());
				agentVectorStoreManager.deleteDocumentsByType(agentId, deleteRequest.getVectorType());
				log.info("Successfully deleted documents by vectorType for agent: {}", agentId);
			}
			else {
				log.warn("Invalid delete request for agent: {}: either id or vectorType must be specified", agentId);
				throw new IllegalArgumentException("Either id or vectorType must be specified.");
			}
			return true;
		}
		catch (Exception e) {
			log.error("Failed to delete documents for agent: {}: {}", agentId, e.getMessage(), e);
			throw new Exception("Failed to delete collection data for agent " + agentId + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Get agent vector storage manager (for other services to use)
	 */
	public AgentVectorStoreManager getAgentVectorStoreManager() {
		return agentVectorStoreManager;
	}

	/**
	 * Get documents from vector store for specified agent Override parent method, use
	 * agent-specific vector storage
	 */
	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
		log.debug("Getting documents for agent: {}, query: {}, vectorType: {}", agentId, query, vectorType);

		if (agentId == null || agentId.trim().isEmpty()) {
			log.warn("AgentId is null or empty, falling back to global search");
			return getDocuments(query, vectorType);
		}

		try {
			// Use agent vector storage manager for search
			List<Document> results = agentVectorStoreManager.similaritySearchWithFilter(agentId, query, 100, // topK
					vectorType);

			log.info("Found {} documents for agent: {}, vectorType: {}", results.size(), agentId, vectorType);
			return results;
		}
		catch (Exception e) {
			log.error("Error getting documents for agent: {}, falling back to global search", agentId, e);
			return getDocuments(query, vectorType);
		}
	}

	/**
	 * 从自定义表关联关系表获取外键信息
	 * @param dbConfig 数据库配置
	 * @param datasourceId 数据源ID
	 * @return 外键信息列表
	 */
	private List<ForeignKeyInfoBO> getForeignKeysFromCustomTable(DbConfig dbConfig, Integer datasourceId) {
		log.info("Getting foreign keys from custom table_relation table for database: {}", dbConfig.getUrl());
		log.info("Using datasource ID: {} for database: {}", datasourceId, dbConfig.getUrl());
		
		try {
			// 从自定义表获取关联关系
			List<ForeignKeyInfoBO> foreignKeys = tableRelationService.getForeignKeysForDatasource(datasourceId);
			log.info("Retrieved {} foreign keys from table_relation table for datasource ID: {}", 
				foreignKeys.size(), datasourceId);
			
			// 打印详细的外键信息
			for (ForeignKeyInfoBO fk : foreignKeys) {
				log.debug("Foreign key: {}.{} -> {}.{}", 
					fk.getTable(), fk.getColumn(), 
					fk.getReferencedTable(), fk.getReferencedColumn());
			}
			
			return foreignKeys;
		} catch (Exception e) {
			log.error("Error getting foreign keys from custom table_relation table", e);
			// 如果出错，返回空列表而不是抛出异常
			return new ArrayList<>();
		}
	}

	/**
	 * 根据数据源配置获取数据源ID
	 * 这里需要根据连接URL、数据库名等信息匹配数据源ID
	 * @param dbConfig 数据库配置
	 * @return 数据源ID，如果找不到则返回null
	 */
	/**
	 * 根据数据源配置获取数据源ID（已废弃，现在直接传递数据源ID）
	 * @deprecated 现在直接通过方法参数传递数据源ID，不再需要匹配
	 */
	@Deprecated
	private Integer getDatasourceIdByConfig(DbConfig dbConfig) {
		log.warn("getDatasourceIdByConfig is deprecated, datasource ID should be passed directly");
		return null;
	}

}
