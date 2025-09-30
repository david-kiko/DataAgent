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

package com.alibaba.cloud.ai.service;

import com.alibaba.cloud.ai.connector.bo.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.entity.TableRelation;
import com.alibaba.cloud.ai.mapper.TableRelationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Table Relation Service Class
 *
 * @author Alibaba Cloud AI
 */
@Service
public class TableRelationService {

    private static final Logger log = LoggerFactory.getLogger(TableRelationService.class);

    @Autowired
    private TableRelationMapper tableRelationMapper;

    /**
     * 根据数据源ID获取表关联关系
     * @param datasourceId 数据源ID
     * @return 表关联关系列表
     */
    public List<TableRelation> getTableRelationsByDatasourceId(Integer datasourceId) {
        log.info("Getting table relations for datasource ID: {}", datasourceId);
        List<TableRelation> relations = tableRelationMapper.selectByDatasourceId(datasourceId);
        log.info("Found {} table relations for datasource ID: {}", relations.size(), datasourceId);
        return relations;
    }

    /**
     * 根据数据源ID和表名获取关联关系
     * @param datasourceId 数据源ID
     * @param tableName 表名
     * @return 表关联关系列表
     */
    public List<TableRelation> getTableRelationsByDatasourceIdAndTable(Integer datasourceId, String tableName) {
        log.info("Getting table relations for datasource ID: {} and table: {}", datasourceId, tableName);
        List<TableRelation> relations = tableRelationMapper.selectByDatasourceIdAndTable(datasourceId, tableName);
        log.info("Found {} table relations for datasource ID: {} and table: {}", relations.size(), datasourceId, tableName);
        return relations;
    }

    /**
     * 根据数据源ID和状态获取关联关系
     * @param datasourceId 数据源ID
     * @param isActive 是否启用
     * @return 表关联关系列表
     */
    public List<TableRelation> getTableRelationsByDatasourceIdAndStatus(Integer datasourceId, Integer isActive) {
        log.info("Getting table relations for datasource ID: {} and status: {}", datasourceId, isActive);
        List<TableRelation> relations = tableRelationMapper.selectByDatasourceIdAndStatus(datasourceId, isActive);
        log.info("Found {} table relations for datasource ID: {} and status: {}", relations.size(), datasourceId, isActive);
        return relations;
    }

    /**
     * 获取所有启用的表关联关系（包括通用关系）
     * @return 表关联关系列表
     */
    public List<TableRelation> getAllActiveRelations() {
        log.info("Getting all active table relations");
        List<TableRelation> relations = tableRelationMapper.selectAllActiveRelations();
        log.info("Found {} active table relations", relations.size());
        return relations;
    }

    /**
     * 将表关联关系转换为ForeignKeyInfoBO格式
     * @param relations 表关联关系列表
     * @return ForeignKeyInfoBO列表
     */
    public List<ForeignKeyInfoBO> convertToForeignKeyInfoBO(List<TableRelation> relations) {
        log.info("Converting {} table relations to ForeignKeyInfoBO format", relations.size());
        
        List<ForeignKeyInfoBO> foreignKeyInfoList = relations.stream()
            .filter(relation -> relation.getIsActive() == 1)
            .map(relation -> {
                log.debug("Converting relation: {} -> {}.{}", 
                    relation.getSourceTable() + "." + relation.getSourceColumn(),
                    relation.getTargetTable() + "." + relation.getTargetColumn(),
                    relation.getDescription());
                
                return ForeignKeyInfoBO.builder()
                    .table(relation.getSourceTable())
                    .column(relation.getSourceColumn())
                    .referencedTable(relation.getTargetTable())
                    .referencedColumn(relation.getTargetColumn())
                    .build();
            })
            .collect(Collectors.toList());
        
        log.info("Successfully converted {} table relations to ForeignKeyInfoBO format", foreignKeyInfoList.size());
        return foreignKeyInfoList;
    }

    /**
     * 根据数据源配置获取表关联关系
     * 优先获取特定数据源的关联关系，如果没有则获取通用关联关系
     * @param datasourceId 数据源ID
     * @return 表关联关系列表
     */
    public List<ForeignKeyInfoBO> getForeignKeysForDatasource(Integer datasourceId) {
        log.info("Getting foreign keys for datasource ID: {}", datasourceId);
        
        List<ForeignKeyInfoBO> result = new ArrayList<>();
        
        // 1. 获取特定数据源的关联关系
        if (datasourceId != null) {
            List<TableRelation> specificRelations = getTableRelationsByDatasourceIdAndStatus(datasourceId, 1);
            if (!specificRelations.isEmpty()) {
                log.info("Found {} specific relations for datasource ID: {}", specificRelations.size(), datasourceId);
                result.addAll(convertToForeignKeyInfoBO(specificRelations));
            }
        }
        
        // 2. 获取通用关联关系（datasource_id为null的）
        List<TableRelation> generalRelations = tableRelationMapper.selectByDatasourceIdAndStatus(null, 1);
        if (!generalRelations.isEmpty()) {
            log.info("Found {} general relations (datasource_id=null)", generalRelations.size());
            result.addAll(convertToForeignKeyInfoBO(generalRelations));
        }
        
        log.info("Total foreign keys found: {} (specific: {}, general: {})", 
            result.size(), 
            datasourceId != null ? getTableRelationsByDatasourceIdAndStatus(datasourceId, 1).size() : 0,
            generalRelations.size());
        
        return result;
    }

    /**
     * 添加表关联关系
     * @param relation 表关联关系
     * @return 是否成功
     */
    public boolean addTableRelation(TableRelation relation) {
        log.info("Adding table relation: {}", relation);
        int result = tableRelationMapper.insert(relation);
        log.info("Table relation added: {}, result: {}", relation.getId(), result > 0);
        return result > 0;
    }

    /**
     * 删除表关联关系
     * @param id 关联关系ID
     * @return 是否成功
     */
    public boolean removeTableRelation(Integer id) {
        log.info("Removing table relation with ID: {}", id);
        int result = tableRelationMapper.deleteById(id);
        log.info("Table relation removed: {}, result: {}", id, result > 0);
        return result > 0;
    }
}
