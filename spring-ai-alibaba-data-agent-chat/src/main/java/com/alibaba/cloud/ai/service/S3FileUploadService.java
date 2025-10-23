package com.alibaba.cloud.ai.service;

import java.io.InputStream;

/**
 * S3文件上传服务接口
 */
public interface S3FileUploadService {
    
    /**
     * 上传文件到S3
     */
    byte[] downloadFile(String objectKey);
    
    /**
     * 删除S3文件
     */
    void deleteFile(String objectKey);
    
    /**
     * 解析CSV文件获取schema信息
     */
    String parseCsvSchema(InputStream inputStream, String originalFilename);
    
}
