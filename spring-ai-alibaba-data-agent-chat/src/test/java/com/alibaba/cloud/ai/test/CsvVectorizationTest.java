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

package com.alibaba.cloud.ai.test;

import com.alibaba.cloud.ai.service.CsvVectorizationService;
import com.alibaba.cloud.ai.service.base.BaseSchemaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV向量化测试
 * 
 * @author zhangshenghang
 */
@SpringBootTest
@ActiveProfiles("test")
public class CsvVectorizationTest {
    
    @Autowired
    private CsvVectorizationService csvVectorizationService;
    
    @Autowired
    private BaseSchemaService baseSchemaService;
    
    /**
     * 测试CSV文件向量化
     */
    @Test
    public void testVectorizeCsvFile() {
        try {
            // 模拟CSV文件数据
            String schemaInfo = """
                {
                    "tableName": "sales_data",
                    "columns": [
                        {"name": "id", "type": "number", "comment": "主键ID"},
                        {"name": "product_name", "type": "string", "comment": "产品名称"},
                        {"name": "sales_amount", "type": "number", "comment": "销售金额"},
                        {"name": "sales_date", "type": "date", "comment": "销售日期"},
                        {"name": "region", "type": "string", "comment": "销售区域"}
                    ],
                    "totalRows": 1000
                }
                """;
            
            // 执行向量化
            csvVectorizationService.vectorizeCsvFile(
                1L,           // csvFileId
                1,            // agentId
                "test-session-001", // sessionId
                "sales_data.csv",   // originalFilename
                schemaInfo    // schemaInfo
            );
            
            System.out.println("CSV文件向量化测试完成");
            
        } catch (Exception e) {
            System.err.println("CSV文件向量化测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试CSV schema召回
     */
    @Test
    public void testCsvSchemaRecall() {
        try {
            String agentId = "1";
            String query = "销售数据分析";
            List<String> keywords = List.of("销售", "金额", "产品");
            
            // 测试CSV文件召回
            var csvFileDocuments = baseSchemaService.getCsvFileDocumentsForAgent(agentId, query);
            System.out.println("CSV文件召回结果数量: " + csvFileDocuments.size());
            
            // 测试CSV列召回
            var csvColumnDocuments = baseSchemaService.getCsvColumnDocumentsByKeywordsForAgent(agentId, keywords);
            System.out.println("CSV列召回结果数量: " + csvColumnDocuments.size());
            
            // 打印召回结果
            csvFileDocuments.forEach(doc -> {
                System.out.println("CSV文件文档: " + doc.getContent());
                System.out.println("元数据: " + doc.getMetadata());
            });
            
            csvColumnDocuments.forEach(columnDocs -> {
                columnDocs.forEach(doc -> {
                    System.out.println("CSV列文档: " + doc.getContent());
                    System.out.println("元数据: " + doc.getMetadata());
                });
            });
            
            System.out.println("CSV schema召回测试完成");
            
        } catch (Exception e) {
            System.err.println("CSV schema召回测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试批量CSV文件向量化
     */
    @Test
    public void testBatchVectorizeCsvFiles() {
        try {
            // 准备测试数据
            List<Map<String, Object>> csvFiles = new ArrayList<>();
            
            // 文件1
            Map<String, Object> file1 = new HashMap<>();
            file1.put("id", 1L);
            file1.put("agentId", 1);
            file1.put("sessionId", "test-session-001");
            file1.put("originalFilename", "sales_data.csv");
            file1.put("schemaInfo", """
                {
                    "tableName": "sales_data",
                    "columns": [
                        {"name": "id", "type": "number", "comment": "主键ID"},
                        {"name": "product_name", "type": "string", "comment": "产品名称"},
                        {"name": "sales_amount", "type": "number", "comment": "销售金额"}
                    ],
                    "totalRows": 1000
                }
                """);
            csvFiles.add(file1);
            
            // 文件2
            Map<String, Object> file2 = new HashMap<>();
            file2.put("id", 2L);
            file2.put("agentId", 1);
            file2.put("sessionId", "test-session-001");
            file2.put("originalFilename", "customer_data.csv");
            file2.put("schemaInfo", """
                {
                    "tableName": "customer_data",
                    "columns": [
                        {"name": "customer_id", "type": "number", "comment": "客户ID"},
                        {"name": "customer_name", "type": "string", "comment": "客户名称"},
                        {"name": "email", "type": "string", "comment": "邮箱地址"}
                    ],
                    "totalRows": 500
                }
                """);
            csvFiles.add(file2);
            
            // 执行批量向量化
            csvVectorizationService.batchVectorizeCsvFiles(csvFiles);
            
            System.out.println("批量CSV文件向量化测试完成");
            
        } catch (Exception e) {
            System.err.println("批量CSV文件向量化测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
