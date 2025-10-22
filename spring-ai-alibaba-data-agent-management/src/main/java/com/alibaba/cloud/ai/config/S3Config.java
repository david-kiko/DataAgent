package com.alibaba.cloud.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3存储配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "s3")
public class S3Config {
    
    /**
     * S3服务端点
     */
    private String endpoint;
    
    /**
     * 访问密钥
     */
    private String accessKey;
    
    /**
     * 秘密密钥
     */
    private String secretKey;
    
    /**
     * 存储桶名称
     */
    private String bucketName;
    
    /**
     * 区域（可选，MinIO等S3兼容存储通常不需要）
     */
    private String region;
}
