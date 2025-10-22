package com.alibaba.cloud.ai.mapper;

import com.alibaba.cloud.ai.entity.CsvFile;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * CSV文件Mapper
 */
@Mapper
public interface CsvFileMapper {
    
    @Insert("""
            INSERT INTO csv_files (agent_id, session_id, original_filename, stored_filename, file_path, 
                                 file_size, file_type, upload_time, status, schema_info, created_time, updated_time)
            VALUES (#{agentId}, #{sessionId}, #{originalFilename}, #{storedFilename}, #{filePath}, 
                   #{fileSize}, #{fileType}, #{uploadTime}, #{status}, #{schemaInfo}, #{createdTime}, #{updatedTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(CsvFile csvFile);
    
    @Select("SELECT * FROM csv_files WHERE id = #{id}")
    CsvFile selectById(Long id);
    
    @Select("SELECT * FROM csv_files WHERE session_id = #{sessionId} AND status = 'ACTIVE'")
    List<CsvFile> selectBySessionId(String sessionId);
    
    @Select("SELECT * FROM csv_files WHERE agent_id = #{agentId} AND session_id = #{sessionId} AND status = 'ACTIVE'")
    List<CsvFile> selectByAgentAndSession(@Param("agentId") Integer agentId, @Param("sessionId") String sessionId);
    
    @Update("""
            UPDATE csv_files SET 
                status = #{status},
                updated_time = #{updatedTime}
            WHERE id = #{id}
            """)
    int updateStatus(CsvFile csvFile);
    
    @Delete("DELETE FROM csv_files WHERE id = #{id}")
    int deleteById(Long id);
}
