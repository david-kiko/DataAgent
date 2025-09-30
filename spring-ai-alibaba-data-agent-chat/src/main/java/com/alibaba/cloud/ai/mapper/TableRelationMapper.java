/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.mapper;

import com.alibaba.cloud.ai.entity.TableRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Table Relation Mapper Interface
 *
 * @author Alibaba Cloud AI
 */
@Mapper
public interface TableRelationMapper extends BaseMapper<TableRelation> {

    /**
     * 根据数据源ID获取表关联关系
     * @param datasourceId 数据源ID
     * @return 表关联关系列表
     */
    List<TableRelation> selectByDatasourceId(@Param("datasourceId") Integer datasourceId);

    /**
     * 根据数据源ID和表名获取关联关系
     * @param datasourceId 数据源ID
     * @param tableName 表名
     * @return 表关联关系列表
     */
    List<TableRelation> selectByDatasourceIdAndTable(@Param("datasourceId") Integer datasourceId, 
                                                     @Param("tableName") String tableName);

    /**
     * 根据数据源ID和状态获取关联关系
     * @param datasourceId 数据源ID
     * @param isActive 是否启用
     * @return 表关联关系列表
     */
    List<TableRelation> selectByDatasourceIdAndStatus(@Param("datasourceId") Integer datasourceId, 
                                                      @Param("isActive") Integer isActive);

    /**
     * 获取所有启用的表关联关系（datasource_id为null的通用关系）
     * @return 表关联关系列表
     */
    List<TableRelation> selectAllActiveRelations();
}
