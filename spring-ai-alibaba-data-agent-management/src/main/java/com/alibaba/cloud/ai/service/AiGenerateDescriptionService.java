/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.service;

import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI Generate Description Service
 */
@Slf4j
@Service
public class AiGenerateDescriptionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LlmService llmService;

    /**
     * Generate business knowledge description using AI
     * @param content input content (may contain SQL)
     * @param agentId agent ID
     * @param datasetId dataset ID
     * @return generated description
     */
    public String generateDescription(String content, Integer agentId, String datasetId) {
        log.info("=== 开始AI生成业务知识描述 ===");
        log.info("输入参数 - agentId: {}, datasetId: {}, content长度: {}", agentId, datasetId, content.length());
        log.info("输入内容: {}", content);
        
        try {
            // Step 1: Extract table names from SQL if present
            log.info("步骤1: 提取SQL表名");
            Set<String> tableNames = extractTableNamesFromSql(content);
            log.info("Extracted table names: {}", tableNames);

            // Step 2: Extract keywords from content
            log.info("步骤2: 提取关键词");
            List<String> keywords = extractKeywords(content);
            log.info("Extracted keywords: {}", keywords);

            // Step 3: Recall business knowledge
            log.info("步骤3: 召回业务知识");
            List<BusinessKnowledgeDTO> businessKnowledge = recallBusinessKnowledge(datasetId, keywords);
            log.info("Recalled business knowledge count: {}", businessKnowledge.size());

            // Step 4: Generate description using LLM
            log.info("步骤4: 调用LLM生成描述");
            String generatedDescription = generateWithLlm(content, tableNames, businessKnowledge);
            log.info("Generated description length: {}", generatedDescription.length());
            log.info("=== AI生成业务知识描述完成 ===");

            return generatedDescription;

        } catch (Exception e) {
            log.error("Error generating description: {}", e.getMessage(), e);
            throw new RuntimeException("AI生成描述失败: " + e.getMessage(), e);
        }
    }

    /**
     * Extract table names from SQL content
     */
    private Set<String> extractTableNamesFromSql(String content) {
        Set<String> tableNames = new HashSet<>();
        
        // Simple regex to find table names after FROM and JOIN keywords
        Pattern pattern = Pattern.compile("(?i)(?:FROM|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String tableName = matcher.group(1).trim();
            if (!tableName.isEmpty()) {
                tableNames.add(tableName);
            }
        }
        
        return tableNames;
    }

    /**
     * Extract keywords from content
     */
    private List<String> extractKeywords(String content) {
        List<String> keywords = new ArrayList<>();
        
        // Extract potential business terms and field names
        // Look for patterns like: field names, business terms, etc.
        Pattern fieldPattern = Pattern.compile("\\b[A-Z_]+[A-Z0-9_]*\\b");
        Matcher fieldMatcher = fieldPattern.matcher(content);
        
        while (fieldMatcher.find()) {
            String keyword = fieldMatcher.group();
            if (keyword.length() > 2) { // Filter out very short matches
                keywords.add(keyword);
            }
        }
        
        // Also extract Chinese business terms
        Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fa5]+");
        Matcher chineseMatcher = chinesePattern.matcher(content);
        
        while (chineseMatcher.find()) {
            String keyword = chineseMatcher.group();
            if (keyword.length() > 1) {
                keywords.add(keyword);
            }
        }
        
        return keywords.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Recall business knowledge based on keywords
     */
    private List<BusinessKnowledgeDTO> recallBusinessKnowledge(String datasetId, List<String> keywords) {
        try {
            if (datasetId != null && !datasetId.trim().isEmpty()) {
                // Query business knowledge from database
                String sql = "SELECT business_term, description, synonyms, is_recall, data_set_id " +
                           "FROM business_knowledge WHERE data_set_id = ? AND is_recall = 1";
                
                return jdbcTemplate.query(sql, new Object[]{datasetId}, (rs, rowNum) -> {
                    return new BusinessKnowledgeDTO(
                        rs.getString("business_term"),
                        rs.getString("description"),
                        rs.getString("synonyms"),
                        rs.getObject("is_recall", Boolean.class),
                        rs.getString("data_set_id")
                    );
                });
            }
        } catch (Exception e) {
            log.warn("Failed to recall business knowledge: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Generate description using LLM
     */
    private String generateWithLlm(String content, Set<String> tableNames, List<BusinessKnowledgeDTO> businessKnowledge) {
        StringBuilder prompt = new StringBuilder();
        
        log.info("开始构建LLM提示词...");
        log.info("输入内容长度: {}", content.length());
        
        prompt.append("你是一个专业的业务分析师，需要将SQL语句或业务描述转换为结构化的业务知识描述。\n\n");
        prompt.append("输入内容：\n").append(content).append("\n\n");
        
        if (!tableNames.isEmpty()) {
            log.info("添加表信息到提示词，表数量: {}", tableNames.size());
            prompt.append("涉及的表：\n");
            for (String tableName : tableNames) {
                prompt.append("- ").append(tableName).append("\n");
            }
            prompt.append("\n");
        } else {
            log.info("未发现表名，跳过表信息");
        }
        
        if (!businessKnowledge.isEmpty()) {
            log.info("添加业务知识到提示词，知识数量: {}", businessKnowledge.size());
            prompt.append("相关业务知识：\n");
            for (BusinessKnowledgeDTO knowledge : businessKnowledge) {
                prompt.append("- ").append(knowledge.getBusinessTerm()).append(": ").append(knowledge.getDescription()).append("\n");
            }
            prompt.append("\n");
        } else {
            log.info("未召回业务知识，跳过业务知识信息");
        }
        
        prompt.append("请根据以上信息，生成结构化的业务知识描述，格式如下：\n");
        prompt.append("计算公式：[如果有计算公式]\n");
        prompt.append("字段说明：[字段名]: [字段含义], [null值处理说明]\n");
        prompt.append("过滤条件：[如果有过滤条件]\n");
        prompt.append("时间过滤字段：[如果有时间过滤]\n");
        prompt.append("涉及表：[表名]\n");
        prompt.append("业务术语：[相关业务术语解释]\n\n");
        prompt.append("请直接输出描述内容，不要包含其他解释：");
        
        String finalPrompt = prompt.toString();
        log.info("LLM提示词构建完成，总长度: {}", finalPrompt.length());
        log.info("=== LLM提示词内容 ===");
        log.info("{}", finalPrompt);
        log.info("=== LLM提示词结束 ===");
        
        log.info("开始调用LLM服务...");
        long startTime = System.currentTimeMillis();
        
        try {
            String result = llmService.call(finalPrompt);
            long endTime = System.currentTimeMillis();
            log.info("LLM调用完成，耗时: {}ms", (endTime - startTime));
            log.info("LLM返回结果长度: {}", result.length());
            log.info("=== LLM返回结果 ===");
            log.info("{}", result);
            log.info("=== LLM返回结果结束 ===");
            return result;
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("LLM调用失败，耗时: {}ms，错误: {}", (endTime - startTime), e.getMessage(), e);
            throw e;
        }
    }
}
