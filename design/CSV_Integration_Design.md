# CSV集成设计方案

## 概述

本文档描述了DataAgent项目中CSV文件分析功能的完整设计方案。该方案采用统一的数据格式和Python处理逻辑，支持CSV和数据库两种数据源的统一分析。

## 设计原则

- **统一架构**：CSV和数据库使用相同的Python处理逻辑
- **数据格式统一**：所有数据源都转换为统一的JSON格式
- **最小侵入性**：保持现有架构稳定，只扩展新功能
- **代码复用**：一套Python分析代码处理所有数据源
- **AI驱动决策**：让LLM根据所有可用信息自动判断执行策略

## 整体架构

### 数据流图

```
用户查询 → QueryRewriteNode → SchemaRecallNode → PlannerNode → PlanExecutorNode
    ↓              ↓                    ↓                ↓              ↓
查询重写       Schema召回           统一计划生成      计划执行        具体分析
    ↓              ↓                    ↓                ↓              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                        AI自动判断执行策略                              │
├─────────────────────────────────────────────────────────────────────────┤
│ LLM根据提示词中的schema信息自动决定：                                 │
│ - 如果有数据库schema → 生成SQL计划                                   │
│ - 如果有CSV schema → 生成Python分析计划                             │
│ - 如果都有 → 生成混合分析计划                                       │
└─────────────────────────────────────────────────────────────────────────┘
    ↓              ↓                    ↓                ↓              ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                        统一Python处理                                  │
├─────────────────────────────────────────────────────────────────────────┤
│ 数据标准化 → 统一JSON格式 → Python分析 → 报告生成                    │
└─────────────────────────────────────────────────────────────────────────┘
```

## 核心组件

### 1. CSV数据向量化服务 (CsvVectorizationService)

**位置**：`spring-ai-alibaba-data-agent-chat` 模块

**功能**：负责CSV文件的向量化存储，将CSV schema信息存储到向量数据库
**架构**：采用与数据库schema相同的架构模式，保持一致性

**实现逻辑**：
```java
@Service
public class CsvVectorizationService {
    @Autowired
    private SimpleVectorStoreService vectorStoreService; // 使用相同的向量存储服务
    
    // 向量化CSV文件信息
    public void vectorizeCsvFile(Long csvFileId, Integer agentId, String sessionId, 
                                String originalFilename, String schemaInfo);
    
    // 向量化CSV列信息
    private void vectorizeCsvColumns(Long csvFileId, Integer agentId, String sessionId, 
                                  String tableName, JSONArray columns);
    
    // 批量向量化
    public void batchVectorizeCsvFiles(List<Map<String, Object>> csvFiles);
}
```

### 2. 统一计划生成节点 (PlannerNode)

**功能**：根据所有可用的schema信息，让LLM自动判断执行策略

**实现逻辑**：
```java
public class PlannerNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 获取所有可用的schema信息
        String databaseSchemas = StateUtils.getStringValue(state, SCHEMA_RECALL_NODE_OUTPUT, "");
        String csvSchemas = StateUtils.getStringValue(state, CSV_SCHEMAS, "");
        String businessKnowledge = StateUtils.getStringValue(state, BUSINESS_KNOWLEDGE, "");
        
        // 2. 统一生成计划，让LLM根据所有可用信息自动判断执行策略
        return generateUnifiedPlan(state, processedQuery, databaseSchemas, csvSchemas, businessKnowledge);
    }
}
```

**优势**：
- **简化架构**：不需要复杂的数据源检测逻辑
- **AI驱动**：让LLM根据上下文自动判断最佳执行策略
- **统一处理**：所有数据源使用相同的计划生成逻辑
- **易于扩展**：新增数据源类型时无需修改检测逻辑

### 2. Schema召回节点 (SchemaRecallNode)

**功能**：统一召回所有可用的schema信息

**实现逻辑**：
```java
public class SchemaRecallNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 召回数据库schema信息
        String databaseSchemas = schemaService.recallSchemas();
        
        // 2. 召回CSV文件schema信息  
        String csvSchemas = csvFileService.getSchemasBySession(sessionId);
        
        // 3. 召回业务知识
        String businessKnowledge = businessKnowledgeService.recallKnowledge();
        
        // 4. 返回所有schema信息
        return Map.of(
            SCHEMA_RECALL_NODE_OUTPUT, databaseSchemas,
            CSV_SCHEMAS, csvSchemas,
            BUSINESS_KNOWLEDGE, businessKnowledge
        );
    }
}
```

### 3. 统一Python执行节点 (PythonExecuteNode)

**功能**：统一处理CSV和数据库数据，转换为统一格式

**核心改进**：
```java
public class PythonExecuteNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String dataSourceType = StateUtils.getStringValue(state, DATA_SOURCE_TYPE, "DATABASE_ONLY");
        
        String pythonCode;
        String inputData;
        
        if ("CSV_ONLY".equals(dataSourceType)) {
            // CSV模式：使用统一的Python代码，数据格式统一
            pythonCode = StateUtils.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
            List<Map<String, Object>> csvSchemas = (List<Map<String, Object>>) state.value(CSV_SCHEMAS).orElse(List.of());
            inputData = convertCsvToStandardFormat(csvSchemas);
        } else {
            // 数据库模式：使用统一的Python代码，数据格式统一
            pythonCode = StateUtils.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
            List<Map<String, String>> sqlResults = StateUtils.getListValue(state, SQL_RESULT_LIST_MEMORY);
            inputData = convertSqlToStandardFormat(sqlResults);
        }
        
        // 统一的Python执行逻辑
        CodePoolExecutorService.TaskRequest taskRequest = new CodePoolExecutorService.TaskRequest(pythonCode, inputData, null);
        // ... 执行逻辑
    }
}
```

## 统一数据格式

### 标准JSON格式

```json
{
  "data": [
    {"column1": "value1", "column2": "value2"},
    {"column1": "value3", "column2": "value4"}
  ],
  "metadata": {
    "source": "database/csv",
    "row_count": 1000,
    "columns": ["column1", "column2"]
  }
}
```

### 数据转换逻辑

#### CSV数据转换
```java
private String convertCsvToStandardFormat(List<Map<String, Object>> csvSchemas) throws Exception {
    Map<String, Object> standardFormat = new HashMap<>();
    
    // 提取数据行
    List<Map<String, Object>> data = new ArrayList<>();
    for (Map<String, Object> schema : csvSchemas) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) schema.get("data");
        if (rows != null) {
            data.addAll(rows);
        }
    }
    
    // 构建元数据
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("source", "csv");
    metadata.put("row_count", data.size());
    if (!csvSchemas.isEmpty()) {
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) csvSchemas.get(0).get("columns");
        metadata.put("columns", columns);
    }
    
    standardFormat.put("data", data);
    standardFormat.put("metadata", metadata);
    
    return objectMapper.writeValueAsString(standardFormat);
}
```

#### SQL结果转换
```java
private String convertSqlToStandardFormat(List<Map<String, String>> sqlResults) throws Exception {
    Map<String, Object> standardFormat = new HashMap<>();
    
    // 转换数据类型
    List<Map<String, Object>> data = new ArrayList<>();
    for (Map<String, String> row : sqlResults) {
        Map<String, Object> convertedRow = new HashMap<>();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            convertedRow.put(entry.getKey(), entry.getValue());
        }
        data.add(convertedRow);
    }
    
    // 构建元数据
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("source", "database");
    metadata.put("row_count", data.size());
    if (!sqlResults.isEmpty()) {
        metadata.put("columns", sqlResults.get(0).keySet());
    }
    
    standardFormat.put("data", data);
    standardFormat.put("metadata", metadata);
    
    return objectMapper.writeValueAsString(standardFormat);
}
```

## 统一Python处理

### Python代码模板

```python
import json
import sys
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
from datetime import datetime

# 设置中文字体支持
plt.rcParams['font.sans-serif'] = ['SimHei', 'Arial Unicode MS']
plt.rcParams['axes.unicode_minus'] = False

try:
    # 从stdin读取统一格式的数据
    input_data = json.load(sys.stdin)
    data = input_data["data"]
    metadata = input_data["metadata"]
    
    # 转换为DataFrame
    df = pd.DataFrame(data)
    
    # 创建图片保存目录
    chart_dir = "/tmp/charts"
    os.makedirs(chart_dir, exist_ok=True)
    
    # 统一的分析逻辑
    result = {
        "analysis": perform_analysis(df),
        "charts": [],  # 图片文件路径列表
        "summary": "分析完成"
    }
    
    # 生成图表并保存
    chart_paths = generate_charts(df, chart_dir)
    result["charts"] = chart_paths
    
    print(json.dumps(result, ensure_ascii=False))
    
except Exception as e:
    import traceback
    traceback.print_exc(file=sys.stderr)
    sys.exit(1)
```

### 图片处理流程

1. **Python生成图片**：保存到`/tmp/charts/`目录
2. **图片上传S3**：PythonExecuteNode自动检测并上传图片
3. **URL替换**：将本地路径替换为S3 URL
4. **报告生成**：ReportGeneratorNode在HTML中引用S3图片URL

## 执行流程

### 场景1：无CSV + 有数据库 (DATABASE_ONLY)
```
用户查询 → QueryRewriteNode → DataSourceDetectNode → KEYWORD_EXTRACT_NODE → 
SCHEMA_RECALL_NODE → NL2SQL_NODE → SQL_EXECUTE_NODE → PythonExecuteNode → ReportGeneratorNode
```

### 场景2：有CSV + 无数据库 (CSV_ONLY)
```
用户查询 → QueryRewriteNode → DataSourceDetectNode → CSV_SCHEMA_NODE → 
CsvAnalyzeNode → PythonExecuteNode → ReportGeneratorNode
```

### 场景3：有CSV + 有数据库 (MIXED_SOURCE)
```
用户查询 → QueryRewriteNode → DataSourceDetectNode → 智能路由 → 
选择主要数据源 → 执行对应流程
```

## 技术要点

### 1. 数据标准化
- 所有数据源都转换为统一的JSON格式
- 包含数据行和元数据信息
- 支持不同数据类型的统一处理

### 2. Python代码统一
- 使用相同的`python-generator.txt`提示词
- 统一的输入输出格式
- 相同的错误处理机制

### 3. 报告生成统一
- ReportGeneratorNode支持不同数据源
- 统一的报告格式和内容结构
- 一致的图表和数据分析展示

## 实现状态

### ✅ 已完成
- [x] 数据源检测节点 (DataSourceDetectNode)
- [x] CSV结构解析节点 (CsvSchemaNode)
- [x] CSV分析节点 (CsvAnalyzeNode)
- [x] 统一Python执行节点 (PythonExecuteNode)
- [x] 数据格式转换逻辑
- [x] 提示词统一 (删除csv-analyze.txt，统一使用python-generator.txt)
- [x] 图片生成方案集成
- [x] PythonExecuteNode图片处理逻辑
- [x] 设计文档清理

### 🔄 进行中
- [ ] 测试场景1和场景2
- [ ] 验证数据格式转换正确性
- [ ] 确认Python代码执行结果
- [ ] 实现S3图片上传功能

### 📋 待完成
- [ ] 场景3混合数据源支持
- [ ] ReportGeneratorNode支持图片URL
- [ ] 性能优化和缓存机制
- [ ] 错误处理和异常情况

## 优势总结

1. **架构统一**：CSV和数据库使用相同的处理流程
2. **代码复用**：一套Python分析代码处理所有数据源
3. **维护简单**：统一的提示词和错误处理逻辑
4. **扩展性强**：新增数据源只需实现格式转换
5. **用户体验一致**：所有数据源的分析结果格式统一

## 注意事项

1. **数据转换性能**：大数据量时需要考虑转换性能
2. **内存管理**：统一格式可能增加内存使用
3. **错误处理**：需要完善的异常处理机制
4. **向后兼容**：确保现有数据库分析功能不受影响
