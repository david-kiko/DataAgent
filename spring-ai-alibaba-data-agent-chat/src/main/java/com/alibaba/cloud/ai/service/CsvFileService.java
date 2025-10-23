package com.alibaba.cloud.ai.service;

import com.alibaba.cloud.ai.entity.CsvFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * CSV文件服务接口
 */
public interface CsvFileService {
    
    /**
     * 上传CSV文件
     */
    CsvFile uploadFile(MultipartFile file, Integer agentId, String sessionId) throws IOException;
    
    /**
     * 根据会话ID获取CSV文件列表
     */
    List<CsvFile> getBySessionId(String sessionId);
    
    /**
     * 根据智能体和会话ID获取CSV文件列表
     */
    List<CsvFile> getByAgentAndSession(Integer agentId, String sessionId);
    
    /**
     * 根据ID获取CSV文件
     */
    CsvFile getById(Long id);
    
    /**
     * 删除CSV文件
     */
    void deleteFile(Long fileId);
    
    /**
     * 物理删除CSV文件
     */
    void physicalDeleteFile(Long fileId);
    
}
