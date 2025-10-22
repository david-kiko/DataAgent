package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.entity.Agent;
import com.alibaba.cloud.ai.entity.CsvFile;
import com.alibaba.cloud.ai.service.AgentService;
import com.alibaba.cloud.ai.service.CsvFileService;
import com.alibaba.cloud.ai.service.S3FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
    
    @Autowired
    private S3FileUploadService s3FileUploadService;
    
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
     * 删除CSV文件
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteCsvFile(@PathVariable("fileId") String fileIdStr) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("收到删除文件请求: fileId={}", fileIdStr);
            
            // 手动转换String到Long
            Long fileId;
            try {
                fileId = Long.parseLong(fileIdStr);
            } catch (NumberFormatException e) {
                log.error("无效的文件ID格式: {}", fileIdStr);
                response.put("success", false);
                response.put("message", "无效的文件ID格式");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 先检查文件是否存在
            CsvFile csvFile = csvFileService.getById(fileId);
            if (csvFile == null) {
                log.warn("文件不存在: fileId={}", fileId);
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.notFound().build();
            }
            
            log.info("找到文件: fileId={}, filename={}, status={}", 
                    fileId, csvFile.getOriginalFilename(), csvFile.getStatus());
            
            // 执行删除
            csvFileService.deleteFile(fileId);
            
            response.put("success", true);
            response.put("message", "文件删除成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("删除CSV文件失败: fileId={}, error={}", fileIdStr, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "删除文件失败：" + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            response.put("details", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 预览CSV文件 - 返回JSON格式的数据用于前端表格显示
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<Map<String, Object>> previewCsvFile(@PathVariable("fileId") String fileIdStr) {
        try {
            Long fileId = Long.parseLong(fileIdStr);
            CsvFile csvFile = csvFileService.getById(fileId);
            if (csvFile == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 从S3下载文件内容
            byte[] fileContent = s3FileUploadService.downloadFile(csvFile.getFilePath());
            
            // 尝试多种编码方式解析CSV内容，避免中文乱码
            String csvContent = null;
            String[] encodings = {"UTF-8", "GBK", "GB2312", "ISO-8859-1"};
            
            for (String encoding : encodings) {
                try {
                    String testContent = new String(fileContent, encoding);
                    // 检查是否包含乱码
                    if (isValidEncoding(testContent)) {
                        csvContent = testContent;
                        log.info("使用编码 {} 成功解析CSV预览内容", encoding);
                        break;
                    }
                } catch (Exception e) {
                    log.debug("编码 {} 解析失败: {}", encoding, e.getMessage());
                }
            }
            
            if (csvContent == null) {
                csvContent = new String(fileContent, "GB2312"); // 默认使用GB2312
                log.warn("所有编码尝试失败，使用默认GB2312编码进行CSV预览");
            }
            
            // 解析CSV内容
            List<Map<String, String>> rows = parseCsvContent(csvContent);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileId", fileId);
            response.put("filename", csvFile.getOriginalFilename());
            response.put("fileSize", csvFile.getFileSize());
            response.put("rows", rows);
            response.put("totalRows", rows.size());
            
            return ResponseEntity.ok(response);
                    
        } catch (Exception e) {
            log.error("预览CSV文件失败: fileId={}, error={}", fileIdStr, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "预览失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * 下载CSV文件 - 直接下载文件
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> downloadCsvFile(@PathVariable("fileId") String fileIdStr) {
        try {
            Long fileId = Long.parseLong(fileIdStr);
            CsvFile csvFile = csvFileService.getById(fileId);
            if (csvFile == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 从S3下载文件内容
            byte[] fileContent = s3FileUploadService.downloadFile(csvFile.getFilePath());
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", csvFile.getOriginalFilename());
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);
                    
        } catch (Exception e) {
            log.error("下载CSV文件失败: fileId={}, error={}", fileIdStr, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 解析CSV内容
     */
    private List<Map<String, String>> parseCsvContent(String csvContent) {
        List<Map<String, String>> rows = new ArrayList<>();
        String[] lines = csvContent.split("\n");
        
        if (lines.length == 0) {
            return rows;
        }
        
        // 第一行作为表头
        String[] headers = parseCsvLine(lines[0]);
        
        // 解析数据行（最多显示前100行）
        int maxRows = Math.min(lines.length - 1, 100);
        for (int i = 1; i <= maxRows; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            
            String[] values = parseCsvLine(lines[i]);
            Map<String, String> row = new HashMap<>();
            
            for (int j = 0; j < headers.length; j++) {
                String header = headers[j].trim();
                String value = (j < values.length) ? values[j].trim() : "";
                row.put(header, value);
            }
            
            rows.add(row);
        }
        
        return rows;
    }
    
    /**
     * 解析CSV行（简单实现，处理逗号分隔）
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        fields.add(currentField.toString());
        return fields.toArray(new String[0]);
    }
    
    /**
     * 检查编码是否有效（不包含乱码）
     */
    private boolean isValidEncoding(String content) {
        if (content == null || content.isEmpty()) {
            log.debug("内容为空，编码无效");
            return false;
        }
        
        // 检查是否包含替换字符（Unicode替换字符，代码点65533）
        if (content.contains("\uFFFD")) {
            log.debug("包含替换字符，编码无效");
            return false;
        }
        
        // 检查是否包含大量连续的问号（可能是编码错误）
        if (content.matches(".*\\?{3,}.*")) {
            log.debug("包含连续问号，编码无效");
            return false;
        }
        
        // 检查是否包含大量乱码字符（超过15%的字符是乱码）
        int totalChars = content.length();
        int garbledChars = 0;
        
        for (char c : content.toCharArray()) {
            // 检查是否是明显的乱码字符
            if (c == '\uFFFD' || // Unicode替换字符
                (c == '?' && content.indexOf(c) != content.lastIndexOf(c)) || // 多个问号
                (c < 32 && c != '\n' && c != '\r' && c != '\t' && c != ' ')) { // 控制字符（除了常见的空白字符）
                garbledChars++;
            }
        }
        
        double garbledRatio = totalChars > 0 ? (garbledChars * 100.0 / totalChars) : 0;
        log.debug("乱码检测: 总字符={}, 乱码字符={}, 乱码比例={:.2f}%", totalChars, garbledChars, garbledRatio);
        
        // 如果乱码字符超过总字符的15%，认为编码无效（放宽阈值）
        if (garbledRatio > 15) {
            log.debug("乱码比例超过15%，编码无效");
            return false;
        }
        
        // 额外检查：如果内容主要是中文字符和常见标点，认为编码有效
        int chineseChars = 0;
        int commonChars = 0;
        for (char c : content.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) { // 中文字符范围
                chineseChars++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || 
                      (c >= '0' && c <= '9') || c == ',' || c == '.' || c == ' ' || c == '\n' || c == '\r') {
                commonChars++;
            }
        }
        
        int validChars = chineseChars + commonChars;
        double validRatio = totalChars > 0 ? (validChars * 100.0 / totalChars) : 0;
        log.debug("有效字符检测: 中文字符={}, 常见字符={}, 有效字符比例={:.2f}%", chineseChars, commonChars, validRatio);
        
        // 如果有效字符比例超过70%，认为编码有效
        if (validRatio > 70) {
            log.debug("有效字符比例超过70%，编码有效");
            return true;
        }
        
        log.debug("编码检测通过");
        return true;
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
    
    /**
     * 获取会话的文件列表
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionFiles(@PathVariable("sessionId") String sessionId) {
        try {
            log.info("获取会话文件列表: sessionId={}", sessionId);
            
            List<CsvFile> files = csvFileService.getBySessionId(sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", files);
            response.put("total", files.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取会话文件列表失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "获取文件列表失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
