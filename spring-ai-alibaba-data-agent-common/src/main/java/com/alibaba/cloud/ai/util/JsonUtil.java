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

package com.alibaba.cloud.ai.util;

import com.alibaba.cloud.ai.enums.StreamResponseType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JsonUtil {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	public static ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	public static String toJson(StreamResponseType type, String data) {
		try {
			String result = objectMapper.writeValueAsString(Map.of("type", type.getValue(), "data", data));
			// 添加日志来调试JSON序列化
			if (data != null && data.contains("```")) {
				System.out.println("JsonUtil.toJson - 输入数据长度: " + data.length());
				System.out.println("JsonUtil.toJson - 输入数据: [" + data + "]");
				System.out.println("JsonUtil.toJson - 输出JSON长度: " + result.length());
				System.out.println("JsonUtil.toJson - 输出JSON: [" + result + "]");
			}
			return result;
		}
		catch (JsonProcessingException e) {
			String fallback = "{\"type\":\"" + type.getValue() + "\",\"data\":\"" + data.replace("\"", "\\\"") + "\"}";
			System.out.println("JsonUtil.toJson - JSON序列化失败，使用fallback: " + fallback);
			return fallback;
		}
	}

}
