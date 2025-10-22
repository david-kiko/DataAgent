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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
            
            // 上传文件到S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Config.getBucketName())
                    .key(objectKey)
                    .contentType(getContentType(fileExtension))
                    .build();
            
            PutObjectResponse response = getS3Client().putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, inputStream.available()));
            
            log.info("文件上传成功: objectKey={}, etag={}", objectKey, response.eTag());
            
            // 构建CSV文件信息
            return CsvFile.builder()
                    .agentId(agentId)
                    .sessionId(sessionId)
                    .originalFilename(originalFilename)
                    .storedFilename(storedFilename)
                    .filePath(objectKey)
                    .fileSize((long) inputStream.available())
                    .fileType(fileExtension)
                    .uploadTime(LocalDateTime.now())
                    .status("ACTIVE")
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
}
