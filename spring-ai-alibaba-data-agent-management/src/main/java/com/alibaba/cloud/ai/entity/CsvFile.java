package com.alibaba.cloud.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CSV文件实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvFile {
    
    private Long id;
    
    private Integer agentId;
    
    private String sessionId;
    
    private String originalFilename;
    
    private String storedFilename;
    
    private String filePath;
    
    private Long fileSize;
    
    private String fileType;
    
    private LocalDateTime uploadTime;
    
    private String status;
    
    private String schemaInfo;
    
    private LocalDateTime createdTime;
    
    private LocalDateTime updatedTime;
}
