package com.alibaba.cloud.ai.service;

import com.alibaba.cloud.ai.config.S3Config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3配置测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "s3.endpoint=http://192.168.30.132:9010",
    "s3.access-key=1tdvQZ5Er0CUSckmLs17",
    "s3.secret-key=Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0",
    "s3.bucket-name=dataagent",
    "s3.region=us-east-1"
})
public class S3ConfigTest {
    
    @Test
    public void testS3Config() {
        S3Config s3Config = new S3Config();
        s3Config.setEndpoint("http://192.168.30.132:9010");
        s3Config.setAccessKey("1tdvQZ5Er0CUSckmLs17");
        s3Config.setSecretKey("Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0");
        s3Config.setBucketName("dataagent");
        s3Config.setRegion("us-east-1");
        
        // 测试配置是否正确设置
        assertEquals("http://192.168.30.132:9010", s3Config.getEndpoint());
        assertEquals("1tdvQZ5Er0CUSckmLs17", s3Config.getAccessKey());
        assertEquals("Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0", s3Config.getSecretKey());
        assertEquals("dataagent", s3Config.getBucketName());
        assertEquals("us-east-1", s3Config.getRegion());
        
        // 测试URL格式
        assertTrue(s3Config.getEndpoint().startsWith("http://"));
        assertTrue(s3Config.getEndpoint().contains("192.168.30.132"));
        assertTrue(s3Config.getEndpoint().contains("9010"));
    }
}
