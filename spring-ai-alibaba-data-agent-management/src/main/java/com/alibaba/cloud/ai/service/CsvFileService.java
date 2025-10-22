package com.alibaba.cloud.ai.service;

import com.alibaba.cloud.ai.entity.CsvFile;
import com.alibaba.cloud.ai.mapper.CsvFileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CSV文件服务
 */
@Slf4j
@Service
public class CsvFileService {
    
    @Autowired
    private CsvFileMapper csvFileMapper;
    
    @Autowired
    private S3FileUploadService s3FileUploadService;
    
    /**
     * 上传CSV文件
     */
    public CsvFile uploadFile(MultipartFile file, Integer agentId, String sessionId) throws IOException {
        // 上传到S3
        CsvFile csvFile = s3FileUploadService.uploadFile(
                file.getInputStream(), 
                file.getOriginalFilename(), 
                sessionId, 
                agentId
        );
        
        // 保存到数据库
        csvFileMapper.insert(csvFile);
        
        log.info("CSV文件上传成功: agentId={}, sessionId={}, filename={}", 
                agentId, sessionId, file.getOriginalFilename());
        
        return csvFile;
    }
    
    /**
     * 根据会话ID获取CSV文件列表
     */
    public List<CsvFile> getBySessionId(String sessionId) {
        return csvFileMapper.selectBySessionId(sessionId);
    }
    
    /**
     * 根据智能体和会话ID获取CSV文件列表
     */
    public List<CsvFile> getByAgentAndSession(Integer agentId, String sessionId) {
        return csvFileMapper.selectByAgentAndSession(agentId, sessionId);
    }
    
    /**
     * 根据ID获取CSV文件
     */
    public CsvFile getById(Long id) {
        return csvFileMapper.selectById(id);
    }
    
    /**
     * 删除CSV文件
     */
    public void deleteFile(Long fileId) {
        try {
            log.info("开始删除CSV文件: fileId={}", fileId);
            
            CsvFile csvFile = csvFileMapper.selectById(fileId);
            if (csvFile == null) {
                log.warn("CSV文件不存在: fileId={}", fileId);
                throw new RuntimeException("文件不存在");
            }
            
            log.info("找到CSV文件: fileId={}, filename={}, status={}, filePath={}", 
                    fileId, csvFile.getOriginalFilename(), csvFile.getStatus(), csvFile.getFilePath());
            
            // 先删除S3上的文件
            try {
                if (csvFile.getFilePath() != null && !csvFile.getFilePath().isEmpty()) {
                    log.info("开始删除S3文件: filePath={}", csvFile.getFilePath());
                    s3FileUploadService.deleteFile(csvFile.getFilePath());
                    log.info("S3文件删除成功: filePath={}", csvFile.getFilePath());
                } else {
                    log.warn("文件路径为空，跳过S3删除: fileId={}", fileId);
                }
            } catch (Exception e) {
                log.error("删除S3文件失败，但继续更新数据库状态: fileId={}, filePath={}, error={}", 
                        fileId, csvFile.getFilePath(), e.getMessage(), e);
                // 不抛出异常，继续更新数据库状态
            }
            
            // 更新状态为已删除
            csvFile.setStatus("DELETED");
            csvFile.setUpdatedTime(LocalDateTime.now());
            
            int updateResult = csvFileMapper.updateStatus(csvFile);
            log.info("更新文件状态结果: fileId={}, updateResult={}", fileId, updateResult);
            
            if (updateResult > 0) {
                log.info("CSV文件删除成功: fileId={}", fileId);
            } else {
                log.warn("CSV文件状态更新失败: fileId={}", fileId);
                throw new RuntimeException("文件状态更新失败");
            }
            
        } catch (Exception e) {
            log.error("删除CSV文件失败: fileId={}, error={}", fileId, e.getMessage(), e);
            throw new RuntimeException("删除文件失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 物理删除CSV文件
     */
    public void physicalDeleteFile(Long fileId) {
        csvFileMapper.deleteById(fileId);
        log.info("CSV文件物理删除成功: fileId={}", fileId);
    }
    
}
