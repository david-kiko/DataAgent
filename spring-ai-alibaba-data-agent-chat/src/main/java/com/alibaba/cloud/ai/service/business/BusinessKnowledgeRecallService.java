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
package com.alibaba.cloud.ai.service.business;

import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessKnowledgeRecallService {

	private static final String FIELD_GET_BY_DATASET_IDS = """
			SELECT
				business_term,
				description,
			       	synonyms,
				is_recall,
				data_set_id
			FROM business_knowledge WHERE data_set_id = ? AND is_recall = 1
			""";

	private final JdbcTemplate jdbcTemplate;

	public BusinessKnowledgeRecallService(JdbcTemplate jdbcTemplate) {
		Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
		this.jdbcTemplate = jdbcTemplate;
	}

	// Get agent fields by data_set_id
	public List<BusinessKnowledgeDTO> getFieldByDataSetId(String dataSetId) {
		return this.jdbcTemplate.query(FIELD_GET_BY_DATASET_IDS, new Object[] { dataSetId }, (rs, rowNum) -> {
			return new BusinessKnowledgeDTO(rs.getString("business_term"), // businessTerm
					rs.getString("description"), // description
					rs.getString("synonyms"), // synonyms
					rs.getObject("is_recall", Boolean.class), // defaultRecall (convert to
																// Boolean)
					rs.getString("data_set_id") // datasetId
			);
		});
	}

	/**
	 * Get relevant business knowledge by keywords for intelligent recall
	 * @param dataSetId dataset ID
	 * @param keywords extracted keywords from user query
	 * @return list of relevant business knowledge
	 */
	public List<BusinessKnowledgeDTO> getRelevantKnowledgeByKeywords(String dataSetId, List<String> keywords) {
		// If no keywords provided, fallback to full recall
		if (CollectionUtils.isEmpty(keywords)) {
			return getFieldByDataSetId(dataSetId);
		}

		// Build dynamic SQL for keyword matching
		StringBuilder sqlBuilder = new StringBuilder();
		sqlBuilder.append("SELECT business_term, description, synonyms, is_recall, data_set_id ");
		sqlBuilder.append("FROM business_knowledge WHERE data_set_id = ? AND is_recall = 1 AND (");

		List<Object> params = new ArrayList<>();
		params.add(dataSetId);

		// Add keyword matching conditions
		for (int i = 0; i < keywords.size(); i++) {
			if (i > 0) {
				sqlBuilder.append(" OR ");
			}
			sqlBuilder.append("(business_term LIKE ? OR description LIKE ? OR synonyms LIKE ?)");
			String keyword = "%" + keywords.get(i) + "%";
			params.add(keyword);
			params.add(keyword);
			params.add(keyword);
		}
		sqlBuilder.append(") ");

		// Add intelligent ordering: exact business_term match first, then by length
		if (!keywords.isEmpty()) {
			sqlBuilder.append("ORDER BY ");
			sqlBuilder.append("CASE WHEN business_term LIKE ? THEN 1 ELSE 2 END, "); // business_term exact match priority
			sqlBuilder.append("LENGTH(business_term) ASC"); // shorter terms first
			params.add("%" + keywords.get(0) + "%"); // first keyword for ordering
		}

		return this.jdbcTemplate.query(sqlBuilder.toString(), params.toArray(), (rs, rowNum) -> {
			return new BusinessKnowledgeDTO(
				rs.getString("business_term"),
				rs.getString("description"),
				rs.getString("synonyms"),
				rs.getObject("is_recall", Boolean.class),
				rs.getString("data_set_id")
			);
		});
	}

}
