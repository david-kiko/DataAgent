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

package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.service.CsvVectorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV向量化控制器
 * 提供CSV数据向量化的API接口
 * 采用与数据库schema相同的架构模式
 * 
 * @author zhangshenghang
 */
@Slf4j
@RestController
@RequestMapping("/api/csv-vectorization")
public class CsvVectorizationController {
    
    @Autowired
    private CsvVectorizationService csvVectorizationService;
    
    /**
     * 向量化单个CSV文件
     */
    @PostMapping("/vectorize")
    public Map<String, Object> vectorizeCsvFile(@RequestBody Map<String, Object> request) {
        try {
            Long csvFileId = Long.valueOf(request.get("csvFileId").toString());
            Integer agentId = (Integer) request.get("agentId");
            String sessionId = (String) request.get("sessionId");
            String originalFilename = (String) request.get("originalFilename");
            String schemaInfo = (String) request.get("schemaInfo");
            
            log.info("收到CSV向量化请求: fileId={}, agentId={}, sessionId={}, filename={}", 
                    csvFileId, agentId, sessionId, originalFilename);
            
            // 执行向量化
            csvVectorizationService.vectorizeCsvFile(csvFileId, agentId, sessionId, originalFilename, schemaInfo);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "CSV文件向量化成功");
            response.put("csvFileId", csvFileId);
            
            return response;
            
        } catch (Exception e) {
            log.error("CSV文件向量化失败: error={}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "CSV文件向量化失败: " + e.getMessage());
            
            return response;
        }
    }
    
    /**
     * 批量向量化CSV文件
     */
    @PostMapping("/batch-vectorize")
    public Map<String, Object> batchVectorizeCsvFiles(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> csvFiles = (List<Map<String, Object>>) request.get("csvFiles");
            
            log.info("收到批量CSV向量化请求: 数量={}", csvFiles != null ? csvFiles.size() : 0);
            
            if (csvFiles == null || csvFiles.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "CSV文件列表为空");
                return response;
            }
            
            // 执行批量向量化
            csvVectorizationService.batchVectorizeCsvFiles(csvFiles);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "批量CSV文件向量化成功");
            response.put("count", csvFiles.size());
            
            return response;
            
        } catch (Exception e) {
            log.error("批量CSV文件向量化失败: error={}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "批量CSV文件向量化失败: " + e.getMessage());
            
            return response;
        }
    }
    
    /**
     * 删除CSV文件的向量化数据
     */
    @DeleteMapping("/remove/{csvFileId}")
    public Map<String, Object> removeCsvVectorization(@PathVariable Long csvFileId, 
                                                     @RequestParam(required = false) Integer agentId) {
        try {
            log.info("收到删除CSV向量化数据请求: fileId={}, agentId={}", csvFileId, agentId);
            
            // 执行删除
            csvVectorizationService.removeCsvVectorization(csvFileId, agentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "CSV向量化数据删除成功");
            response.put("csvFileId", csvFileId);
            
            return response;
            
        } catch (Exception e) {
            log.error("删除CSV向量化数据失败: fileId={}, error={}", csvFileId, e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除CSV向量化数据失败: " + e.getMessage());
            
            return response;
        }
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "CSV Vectorization Service");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
