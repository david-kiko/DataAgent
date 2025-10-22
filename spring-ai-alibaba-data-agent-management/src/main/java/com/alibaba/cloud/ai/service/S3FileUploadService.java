package com.alibaba.cloud.ai.service;

import com.alibaba.cloud.ai.config.S3Config;
import com.alibaba.cloud.ai.entity.CsvFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.core.ResponseBytes;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S3文件上传服务
 */
@Slf4j
@Service
public class S3FileUploadService {
    
    @Autowired
    private S3Config s3Config;
    
    private S3Client s3Client;
    
    /**
     * 获取S3客户端
     */
    private S3Client getS3Client() {
        if (s3Client == null) {
            try {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(s3Config.getAccessKey(), s3Config.getSecretKey());
                
                // 构建S3客户端
                var builder = S3Client.builder()
                        .credentialsProvider(StaticCredentialsProvider.create(credentials));
                
                // 设置endpoint，处理URL格式问题
                String endpoint = s3Config.getEndpoint();
                if (endpoint != null && !endpoint.trim().isEmpty()) {
                    // 确保endpoint格式正确
                    if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                        endpoint = "http://" + endpoint;
                    }
                    builder.endpointOverride(java.net.URI.create(endpoint));
                }
                
                // 对于S3兼容存储（如rustfs），设置默认region
                String region = s3Config.getRegion();
                if (region == null || region.trim().isEmpty()) {
                    region = "us-east-1"; // 默认region
                }
                builder.region(Region.of(region));
                
                s3Client = builder.build();
                log.info("S3客户端初始化成功: endpoint={}, region={}", endpoint, region);
                
            } catch (Exception e) {
                log.error("S3客户端初始化失败: {}", e.getMessage(), e);
                throw new RuntimeException("S3客户端初始化失败: " + e.getMessage(), e);
            }
        }
        return s3Client;
    }
    
    /**
     * 上传文件到S3
     * @param inputStream 文件输入流
     * @param originalFilename 原始文件名
     * @param sessionId 会话ID
     * @param agentId 智能体ID
     * @return 上传结果信息
     */
    public CsvFile uploadFile(InputStream inputStream, String originalFilename, String sessionId, Integer agentId) {
        try {
            log.info("开始上传文件: filename={}, sessionId={}, agentId={}", originalFilename, sessionId, agentId);
            
            // 生成存储文件名
            String fileExtension = getFileExtension(originalFilename);
            String storedFilename = generateStoredFilename(originalFilename, fileExtension);
            
            // 构建S3对象键（路径）
            String objectKey = buildObjectKey(sessionId, storedFilename);
            
            // 解析CSV schema信息（仅对CSV文件）
            String schemaInfo = "{}";
            long fileSize = 0;
            
            if ("csv".equalsIgnoreCase(fileExtension)) {
                try {
                    // 将InputStream转换为字节数组，以便多次读取
                    byte[] fileBytes = inputStream.readAllBytes();
                    fileSize = fileBytes.length;
                    log.info("文件大小: {} bytes", fileSize);
                    
                    InputStream schemaStream = new java.io.ByteArrayInputStream(fileBytes);
                    InputStream uploadStream = new java.io.ByteArrayInputStream(fileBytes);
                    
                    schemaInfo = parseCsvSchema(schemaStream, originalFilename);
                    log.info("CSV schema解析完成: {}", schemaInfo);
                    
                    // 使用新的stream进行上传
                    inputStream = uploadStream;
                } catch (Exception e) {
                    log.warn("CSV schema解析失败，使用默认值: {}", e.getMessage());
                    // 如果解析失败，重新获取文件大小
                    fileSize = inputStream.available();
                }
            } else {
                // 非CSV文件，直接获取文件大小
                fileSize = inputStream.available();
                log.info("文件大小: {} bytes", fileSize);
            }
            
            // 上传文件到S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(objectKey)
                    .contentType(getContentType(fileExtension))
                    .build();
            
            PutObjectResponse response = getS3Client().putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, fileSize));
            
            log.info("文件上传成功: objectKey={}, etag={}, fileSize={}", objectKey, response.eTag(), fileSize);
            
            // 构建CSV文件信息
            return CsvFile.builder()
                    .agentId(agentId)
                    .sessionId(sessionId)
                    .originalFilename(originalFilename)
                    .storedFilename(storedFilename)
                    .filePath(objectKey)
                    .fileSize(fileSize)
                    .fileType(fileExtension)
                    .uploadTime(LocalDateTime.now())
                    .status("ACTIVE")
                    .schemaInfo(schemaInfo)
                    .createdTime(LocalDateTime.now())
                    .updatedTime(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("文件上传失败: filename={}, sessionId={}, agentId={}, error={}", 
                    originalFilename, sessionId, agentId, e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
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
     * 生成存储文件名
     */
    private String generateStoredFilename(String originalFilename, String fileExtension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s_%s_%s.%s", 
                originalFilename.substring(0, Math.min(originalFilename.lastIndexOf("."), 20)), 
                timestamp, 
                uuid, 
                fileExtension);
    }
    
    /**
     * 构建S3对象键（路径）
     */
    private String buildObjectKey(String sessionId, String storedFilename) {
        return String.format("csv-files/%s/%s", sessionId, storedFilename);
    }
    
    /**
     * 获取内容类型
     */
    private String getContentType(String fileExtension) {
        switch (fileExtension.toLowerCase()) {
            case "csv":
                return "text/csv";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls":
                return "application/vnd.ms-excel";
            default:
                return "application/octet-stream";
        }
    }
    
    /**
     * 从S3下载文件
     */
    public byte[] downloadFile(String objectKey) {
        try {
            log.info("开始下载文件: objectKey={}", objectKey);
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(objectKey)
                    .build();
            
            ResponseBytes<GetObjectResponse> response = getS3Client().getObjectAsBytes(getObjectRequest);
            byte[] fileContent = response.asByteArray();
            
            log.info("文件下载成功: objectKey={}, size={} bytes", objectKey, fileContent.length);
            return fileContent;
            
        } catch (Exception e) {
            log.error("文件下载失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除S3文件
     */
    public void deleteFile(String objectKey) {
        try {
            log.info("开始删除S3文件: objectKey={}", objectKey);
            
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(objectKey)
                    .build();
                    
            getS3Client().deleteObject(deleteObjectRequest);
            log.info("S3文件删除成功: objectKey={}", objectKey);
            
        } catch (Exception e) {
            log.error("S3文件删除失败: objectKey={}, error={}", objectKey, e.getMessage(), e);
            throw new RuntimeException("S3文件删除失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析CSV文件获取schema信息
     */
    public String parseCsvSchema(InputStream inputStream, String originalFilename) {
        try {
            // 读取CSV文件的前几行来解析schema
            byte[] buffer = new byte[8192]; // 读取前8KB
            int bytesRead = inputStream.read(buffer);
            
            // 尝试多种编码方式解析CSV内容
            String csvContent = null;
            String[] encodings = {"UTF-8", "GBK", "GB2312", "ISO-8859-1"};
            
            for (String encoding : encodings) {
                try {
                    String testContent = new String(buffer, 0, bytesRead, encoding);
                    log.debug("尝试编码 {}: 内容长度={}, 前100字符={}", encoding, testContent.length(), 
                             testContent.length() > 100 ? testContent.substring(0, 100) : testContent);
                    
                    // 检查是否包含乱码
                    boolean isValid = isValidEncoding(testContent);
                    log.debug("编码 {} 有效性检查: {}", encoding, isValid);
                    
                    if (isValid) {
                        csvContent = testContent;
                        log.info("使用编码 {} 成功解析CSV内容", encoding);
                        break;
                    } else {
                        log.debug("编码 {} 被判定为无效", encoding);
                    }
                } catch (Exception e) {
                    log.debug("编码 {} 解析失败: {}", encoding, e.getMessage());
                }
            }
            
            if (csvContent == null) {
                csvContent = new String(buffer, 0, bytesRead, "GB2312"); // 默认使用GB2312
                log.warn("所有编码尝试失败，使用默认GB2312编码");
            }
            
            // 解析CSV内容
            String[] lines = csvContent.split("\n");
            if (lines.length == 0) {
                return "{}";
            }
            
            // 第一行作为表头
            String[] headers = parseCsvLine(lines[0]);
            
            // 分析数据类型（基于前几行数据）
            List<Map<String, Object>> schemaColumns = new ArrayList<>();
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].trim();
                if (header.isEmpty()) {
                    continue;
                }
                
                Map<String, Object> column = new HashMap<>();
                column.put("name", header);
                column.put("type", inferDataType(lines, i));
                column.put("comment", ""); // 可以后续通过AI分析生成
                
                schemaColumns.add(column);
            }
            
            // 构建schema信息
            Map<String, Object> schema = new HashMap<>();
            schema.put("tableName", originalFilename.replaceAll("\\.[^.]*$", "")); // 去掉扩展名
            schema.put("columns", schemaColumns);
            schema.put("totalRows", lines.length - 1); // 减去表头行
            
            // 转换为JSON字符串，确保中文字符正确编码
            return com.alibaba.fastjson.JSON.toJSONString(schema, com.alibaba.fastjson.serializer.SerializerFeature.DisableCircularReferenceDetect);
            
        } catch (Exception e) {
            log.error("解析CSV schema失败: {}", e.getMessage(), e);
            return "{}";
        }
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
     * 推断数据类型
     */
    private String inferDataType(String[] lines, int columnIndex) {
        if (lines.length < 2) {
            return "string";
        }
        
        // 检查前几行数据来推断类型
        int sampleSize = Math.min(5, lines.length - 1);
        boolean isNumeric = true;
        boolean isDate = true;
        
        for (int i = 1; i <= sampleSize; i++) {
            if (i >= lines.length) break;
            
            String[] values = parseCsvLine(lines[i]);
            if (columnIndex >= values.length) {
                continue;
            }
            
            String value = values[columnIndex].trim();
            if (value.isEmpty()) {
                continue;
            }
            
            // 检查是否为数字
            if (isNumeric && !isNumeric(value)) {
                isNumeric = false;
            }
            
            // 检查是否为日期
            if (isDate && !isDate(value)) {
                isDate = false;
            }
        }
        
        if (isDate) {
            return "date";
        } else if (isNumeric) {
            return "number";
        } else {
            return "string";
        }
    }
    
    /**
     * 检查是否为数字
     */
    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 检查是否为日期
     */
    private boolean isDate(String str) {
        // 简单的日期格式检查
        return str.matches("\\d{4}-\\d{2}-\\d{2}") || 
               str.matches("\\d{2}/\\d{2}/\\d{4}") ||
               str.matches("\\d{4}/\\d{2}/\\d{2}");
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
}
