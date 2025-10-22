package com.alibaba.cloud.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 环境变量测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "s3.endpoint=http://192.168.30.132:9010",
    "s3.access-key=1tdvQZ5Er0CUSckmLs17",
    "s3.secret-key=Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0",
    "s3.bucket-name=dataagent",
    "s3.region=us-east-1"
})
public class EnvironmentVariableTest {
    
    @Autowired
    private S3Config s3Config;
    
    @Test
    public void testS3ConfigInjection() {
        // 验证配置是否正确注入
        assertNotNull(s3Config);
        assertEquals("http://192.168.30.132:9010", s3Config.getEndpoint());
        assertEquals("1tdvQZ5Er0CUSckmLs17", s3Config.getAccessKey());
        assertEquals("Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0", s3Config.getSecretKey());
        assertEquals("dataagent", s3Config.getBucketName());
        assertEquals("us-east-1", s3Config.getRegion());
        
        // 验证endpoint格式
        assertTrue(s3Config.getEndpoint().startsWith("http://"));
        assertFalse(s3Config.getEndpoint().contains("${"));
        assertFalse(s3Config.getAccessKey().contains("${"));
        assertFalse(s3Config.getSecretKey().contains("${"));
        assertFalse(s3Config.getBucketName().contains("${"));
        assertFalse(s3Config.getRegion().contains("${"));
    }
    
    @Test
    public void testEnvironmentVariableSubstitution() {
        // 测试环境变量替换是否生效
        String endpoint = s3Config.getEndpoint();
        String accessKey = s3Config.getAccessKey();
        String secretKey = s3Config.getSecretKey();
        String bucketName = s3Config.getBucketName();
        String region = s3Config.getRegion();
        
        // 确保没有未替换的变量占位符
        assertFalse(endpoint.contains("${S3_ENDPOINT}"));
        assertFalse(accessKey.contains("${S3_ACCESS_KEY}"));
        assertFalse(secretKey.contains("${S3_SECRET_KEY}"));
        assertFalse(bucketName.contains("${S3_BUCKET_NAME}"));
        assertFalse(region.contains("${S3_REGION}"));
        
        // 确保值是实际的配置值，不是变量名
        assertNotEquals("${S3_ENDPOINT:http://192.168.30.132:9010}", endpoint);
        assertNotEquals("${S3_ACCESS_KEY:1tdvQZ5Er0CUSckmLs17}", accessKey);
        assertNotEquals("${S3_SECRET_KEY:Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0}", secretKey);
        assertNotEquals("${S3_BUCKET_NAME:dataagent}", bucketName);
        assertNotEquals("${S3_REGION:us-east-1}", region);
    }
}
