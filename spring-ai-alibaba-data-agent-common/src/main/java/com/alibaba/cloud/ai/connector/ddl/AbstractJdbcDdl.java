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
package com.alibaba.cloud.ai.connector.ddl;

import com.alibaba.cloud.ai.connector.bo.ColumnInfoBO;
import com.alibaba.cloud.ai.connector.bo.DatabaseInfoBO;
import com.alibaba.cloud.ai.connector.bo.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.connector.bo.ResultSetBO;
import com.alibaba.cloud.ai.connector.bo.SchemaInfoBO;
import com.alibaba.cloud.ai.connector.bo.TableInfoBO;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractJdbcDdl implements Ddl {

	@Deprecated
	public abstract List<DatabaseInfoBO> showDatabases(Connection connection);

	public abstract List<SchemaInfoBO> showSchemas(Connection connection);

	public abstract List<TableInfoBO> showTables(Connection connection, String schema, String tablePattern);

	public abstract List<TableInfoBO> fetchTables(Connection connection, String schema, List<String> tables);

	public abstract List<ColumnInfoBO> showColumns(Connection connection, String schema, String table);

	public abstract List<ForeignKeyInfoBO> showForeignKeys(Connection connection, String schema, List<String> tables);

	public abstract List<String> sampleColumn(Connection connection, String schema, String table, String column);

	/**
	 * 批量获取多个字段的样本数据
	 * @param connection 数据库连接
	 * @param schema 数据库schema
	 * @param table 表名
	 * @param columns 字段名列表
	 * @return 字段名到样本数据的映射
	 */
	public Map<String, List<String>> batchSampleColumns(Connection connection, String schema, String table, List<String> columns) {
		Map<String, List<String>> result = new HashMap<>();
		for (String column : columns) {
			result.put(column, sampleColumn(connection, schema, table, column));
		}
		return result;
	}

	public abstract ResultSetBO scanTable(Connection connection, String schema, String table);

}
