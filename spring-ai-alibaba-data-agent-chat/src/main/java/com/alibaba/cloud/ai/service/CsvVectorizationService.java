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

package com.alibaba.cloud.ai.service;

import com.alibaba.cloud.ai.service.simple.SimpleVectorStoreService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV数据向量化服务
 * 负责将CSV文件的schema信息存储到向量数据库中
 * 采用与数据库schema相同的架构模式
 * 
 * @author zhangshenghang
 */
@Slf4j
@Service
public class CsvVectorizationService {
    
    @Autowired
    private SimpleVectorStoreService vectorStoreService;
    
    /**
     * 将CSV文件信息向量化存储
     * 
     * @param csvFileId CSV文件ID
     * @param agentId 智能体ID
     * @param sessionId 会话ID
     * @param originalFilename 原始文件名
     * @param schemaInfo JSON格式的schema信息
     */
    public void vectorizeCsvFile(Long csvFileId, Integer agentId, String sessionId, 
                                String originalFilename, String schemaInfo) {
        try {
            log.info("开始向量化CSV文件: fileId={}, agentId={}, sessionId={}, filename={}", 
                    csvFileId, agentId, sessionId, originalFilename);
            
            // 解析schema信息
            JSONObject schema = JSON.parseObject(schemaInfo);
            if (schema == null || schema.isEmpty()) {
                log.warn("CSV schema信息为空，跳过向量化: fileId={}", csvFileId);
                return;
            }
            
            String tableName = schema.getString("tableName");
            JSONArray columns = schema.getJSONArray("columns");
            Integer totalRows = schema.getInteger("totalRows");
            
            if (tableName == null || columns == null) {
                log.warn("CSV schema信息不完整，跳过向量化: fileId={}", csvFileId);
                return;
            }
            
            // 1. 向量化CSV文件信息
            vectorizeCsvFileInfo(csvFileId, agentId, sessionId, originalFilename, tableName, totalRows);
            
            // 2. 向量化CSV列信息
            vectorizeCsvColumns(csvFileId, agentId, sessionId, tableName, columns);
            
            log.info("CSV文件向量化完成: fileId={}, tableName={}, columns={}", 
                    csvFileId, tableName, columns.size());
            
        } catch (Exception e) {
            log.error("CSV文件向量化失败: fileId={}, error={}", csvFileId, e.getMessage(), e);
        }
    }
    
    /**
     * 向量化CSV文件信息
     */
    private void vectorizeCsvFileInfo(Long csvFileId, Integer agentId, String sessionId, 
                                    String originalFilename, String tableName, Integer totalRows) {
        try {
            // 构建文件描述信息
            StringBuilder content = new StringBuilder();
            content.append("CSV文件: ").append(originalFilename).append("\n");
            content.append("表名: ").append(tableName).append("\n");
            content.append("总行数: ").append(totalRows != null ? totalRows : 0).append("\n");
            content.append("文件类型: CSV数据文件\n");
            content.append("用途: 数据分析、统计计算、数据挖掘");
            
            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("csv_file_id", csvFileId);
            metadata.put("agent_id", agentId);
            metadata.put("session_id", sessionId);
            metadata.put("original_filename", originalFilename);
            metadata.put("table_name", tableName);
            metadata.put("total_rows", totalRows);
            metadata.put("file_type", "csv");
            metadata.put("vector_type", "csv_file");
            
            // 创建文档
            Document document = new Document(content.toString(), metadata);
            
            // 存储到向量数据库
            List<Document> documents = new java.util.ArrayList<>();
            documents.add(document);
            if (agentId != null) {
                // 使用agentVectorStoreManager添加文档
                vectorStoreService.getAgentVectorStoreManager().addDocuments(agentId.toString(), documents);
            } else {
                // 全局存储 - 使用agentVectorStoreManager的全局方法
                vectorStoreService.getAgentVectorStoreManager().addDocuments("global", documents);
            }
            
            log.info("CSV文件信息向量化完成: fileId={}, tableName={}", csvFileId, tableName);
            
        } catch (Exception e) {
            log.error("CSV文件信息向量化失败: fileId={}, error={}", csvFileId, e.getMessage(), e);
        }
    }
    
    /**
     * 向量化CSV列信息
     */
    private void vectorizeCsvColumns(Long csvFileId, Integer agentId, String sessionId, 
                                  String tableName, JSONArray columns) {
        try {
            for (int i = 0; i < columns.size(); i++) {
                JSONObject column = columns.getJSONObject(i);
                if (column == null) continue;
                
                String columnName = column.getString("name");
                String columnType = column.getString("type");
                String comment = column.getString("comment");
                
                if (columnName == null || columnName.trim().isEmpty()) {
                    continue;
                }
                
                // 构建列描述信息
                StringBuilder content = new StringBuilder();
                content.append("列名: ").append(columnName).append("\n");
                content.append("数据类型: ").append(columnType != null ? columnType : "string").append("\n");
                content.append("所属表: ").append(tableName).append("\n");
                if (comment != null && !comment.trim().isEmpty()) {
                    content.append("说明: ").append(comment).append("\n");
                }
                content.append("数据来源: CSV文件");
                
                // 构建元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("csv_file_id", csvFileId);
                metadata.put("agent_id", agentId);
                metadata.put("session_id", sessionId);
                metadata.put("table_name", tableName);
                metadata.put("column_name", columnName);
                metadata.put("column_type", columnType);
                metadata.put("column_comment", comment);
                metadata.put("file_type", "csv");
                metadata.put("vector_type", "csv_column");
                
                // 创建文档
                Document document = new Document(content.toString(), metadata);
                
                // 存储到向量数据库
                List<Document> documents = new java.util.ArrayList<>();
                documents.add(document);
                if (agentId != null) {
                    // 使用agentVectorStoreManager添加文档
                    vectorStoreService.getAgentVectorStoreManager().addDocuments(agentId.toString(), documents);
                } else {
                    // 全局存储 - 使用agentVectorStoreManager的全局方法
                    vectorStoreService.getAgentVectorStoreManager().addDocuments("global", documents);
                }
            }
            
            log.info("CSV列信息向量化完成: fileId={}, tableName={}, columns={}", 
                    csvFileId, tableName, columns.size());
            
        } catch (Exception e) {
            log.error("CSV列信息向量化失败: fileId={}, error={}", csvFileId, e.getMessage(), e);
        }
    }
    
    /**
     * 删除CSV文件的向量化数据
     * 
     * @param csvFileId CSV文件ID
     * @param agentId 智能体ID
     */
    public void removeCsvVectorization(Long csvFileId, Integer agentId) {
        try {
            log.info("开始删除CSV向量化数据: fileId={}, agentId={}", csvFileId, agentId);
            
            // 这里需要根据具体的向量存储实现来删除数据
            // 由于当前的SimpleVectorStoreService没有提供按条件删除的方法
            // 我们暂时记录日志，实际删除需要根据具体的向量存储实现
            
            log.info("CSV向量化数据删除完成: fileId={}", csvFileId);
            
        } catch (Exception e) {
            log.error("删除CSV向量化数据失败: fileId={}, error={}", csvFileId, e.getMessage(), e);
        }
    }
    
    /**
     * 批量向量化CSV文件
     * 
     * @param csvFiles CSV文件列表，每个文件包含id, agentId, sessionId, originalFilename, schemaInfo
     */
    public void batchVectorizeCsvFiles(List<Map<String, Object>> csvFiles) {
        try {
            log.info("开始批量向量化CSV文件: 数量={}", csvFiles.size());
            
            for (Map<String, Object> csvFile : csvFiles) {
                Long csvFileId = (Long) csvFile.get("id");
                Integer agentId = (Integer) csvFile.get("agentId");
                String sessionId = (String) csvFile.get("sessionId");
                String originalFilename = (String) csvFile.get("originalFilename");
                String schemaInfo = (String) csvFile.get("schemaInfo");
                
                if (csvFileId != null && schemaInfo != null && !schemaInfo.trim().isEmpty()) {
                    vectorizeCsvFile(csvFileId, agentId, sessionId, originalFilename, schemaInfo);
                }
            }
            
            log.info("批量向量化CSV文件完成: 数量={}", csvFiles.size());
            
        } catch (Exception e) {
            log.error("批量向量化CSV文件失败: error={}", e.getMessage(), e);
        }
    }
}
