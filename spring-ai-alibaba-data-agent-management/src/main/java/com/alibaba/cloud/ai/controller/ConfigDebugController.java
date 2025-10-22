package com.alibaba.cloud.ai.controller;

import com.alibaba.cloud.ai.config.S3Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置调试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
public class ConfigDebugController {
    
    @Autowired
    private S3Config s3Config;
    
    /**
     * 获取S3配置信息（用于调试）
     */
    @GetMapping("/s3-config")
    public Map<String, Object> getS3Config() {
        Map<String, Object> config = new HashMap<>();
        
        try {
            config.put("endpoint", s3Config.getEndpoint());
            config.put("accessKey", s3Config.getAccessKey());
            config.put("secretKey", s3Config.getSecretKey());
            config.put("bucketName", s3Config.getBucketName());
            config.put("region", s3Config.getRegion());
            
            // 检查是否有未替换的变量
            config.put("hasUnresolvedVariables", 
                (s3Config.getEndpoint() != null && s3Config.getEndpoint().contains("${")) ||
                (s3Config.getAccessKey() != null && s3Config.getAccessKey().contains("${")) ||
                (s3Config.getSecretKey() != null && s3Config.getSecretKey().contains("${")) ||
                (s3Config.getBucketName() != null && s3Config.getBucketName().contains("${")) ||
                (s3Config.getRegion() != null && s3Config.getRegion().contains("${"))
            );
            
            // 检查endpoint格式
            config.put("endpointFormatValid", 
                s3Config.getEndpoint() != null && 
                (s3Config.getEndpoint().startsWith("http://") || s3Config.getEndpoint().startsWith("https://"))
            );
            
            log.info("S3配置调试信息: {}", config);
            
        } catch (Exception e) {
            log.error("获取S3配置失败", e);
            config.put("error", e.getMessage());
        }
        
        return config;
    }
}
