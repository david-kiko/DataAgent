package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.entity.Agent;
import com.alibaba.cloud.ai.entity.CsvFile;
import com.alibaba.cloud.ai.service.AgentService;
import com.alibaba.cloud.ai.service.CsvFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/csv")
public class CsvFileController {
    
    @Autowired
    private CsvFileService csvFileService;
    
    @Autowired
    private AgentService agentService;
    
    /**
     * 上传CSV文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadCsvFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("agentId") Integer agentId,
            @RequestParam("sessionId") String sessionId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 检查智能体是否允许CSV上传
            Agent agent = agentService.findById(Long.valueOf(agentId));
            if (agent == null) {
                response.put("success", false);
                response.put("message", "智能体不存在");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (agent.getCsvUploadEnabled() == null || agent.getCsvUploadEnabled() != 1) {
                response.put("success", false);
                response.put("message", "该智能体不允许上传CSV文件");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 检查文件大小
            Long maxFileSize = agent.getCsvMaxFileSize() != null ? agent.getCsvMaxFileSize() : 10485760L; // 默认10MB
            if (file.getSize() > maxFileSize) {
                response.put("success", false);
                response.put("message", String.format("文件大小超过限制，最大允许%dMB", maxFileSize / 1024 / 1024));
                return ResponseEntity.badRequest().body(response);
            }
            
            // 检查文件类型
            String allowedTypes = agent.getCsvAllowedTypes() != null ? agent.getCsvAllowedTypes() : "csv,xlsx";
            String fileExtension = getFileExtension(file.getOriginalFilename());
            if (!isAllowedFileType(fileExtension, allowedTypes)) {
                response.put("success", false);
                response.put("message", "不支持的文件类型，允许的类型：" + allowedTypes);
                return ResponseEntity.badRequest().body(response);
            }
            
            // 上传文件
            CsvFile csvFile = csvFileService.uploadFile(file, agentId, sessionId);
            
            response.put("success", true);
            response.put("message", "文件上传成功");
            response.put("data", csvFile);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("CSV文件上传失败: agentId={}, sessionId={}, filename={}, error={}", 
                    agentId, sessionId, file.getOriginalFilename(), e.getMessage(), e);
            response.put("success", false);
            response.put("message", "文件上传失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取会话中的CSV文件列表
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionCsvFiles(@PathVariable String sessionId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<CsvFile> csvFiles = csvFileService.getBySessionId(sessionId);
            
            response.put("success", true);
            response.put("data", csvFiles);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取CSV文件列表失败", e);
            response.put("success", false);
            response.put("message", "获取文件列表失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 删除CSV文件
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteCsvFile(@PathVariable Long fileId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            csvFileService.deleteFile(fileId);
            
            response.put("success", true);
            response.put("message", "文件删除成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("删除CSV文件失败", e);
            response.put("success", false);
            response.put("message", "删除文件失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 检查文件类型是否允许
     */
    private boolean isAllowedFileType(String fileExtension, String allowedTypes) {
        if (allowedTypes == null || allowedTypes.trim().isEmpty()) {
            return true;
        }
        
        String[] types = allowedTypes.split(",");
        for (String type : types) {
            if (type.trim().equalsIgnoreCase(fileExtension)) {
                return true;
            }
        }
        return false;
    }
}
