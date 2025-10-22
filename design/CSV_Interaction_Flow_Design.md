# CSV交互流程设计

## 整体交互流程图

```
用户输入查询
    ↓
QueryRewriteNode (查询重写)
    ↓
数据源检测 (新增逻辑)
    ↓
┌─────────────────────────────────────────────────────────────┐
│                    数据源判断                                │
├─────────────────────────────────────────────────────────────┤
│ 场景1: 无CSV + 有数据库 → DATABASE_ONLY                    │
│ 场景2: 有CSV + 无数据库 → CSV_ONLY                        │
│ 场景3: 有CSV + 有数据库 → MIXED_SOURCE                    │
└─────────────────────────────────────────────────────────────┘
    ↓
PlannerNode (计划生成)
    ↓
┌─────────────────────────────────────────────────────────────┐
│                    执行计划分发                              │
├─────────────────────────────────────────────────────────────┤
│ DATABASE_ONLY → 原有NL2SQL流程                            │
│ CSV_ONLY → CSV分析流程                                    │
│ MIXED_SOURCE → 混合分析流程                               │
└─────────────────────────────────────────────────────────────┘
    ↓
PlanExecutorNode (计划执行)
    ↓
┌─────────────────────────────────────────────────────────────┐
│                    具体执行路径                              │
├─────────────────────────────────────────────────────────────┤
│ 路径1: NL2SQL → 数据库查询 → Python分析 → 报告生成        │
│ 路径2: CSV解析 → Python分析 → 报告生成                    │
│ 路径3: 智能路由 → 根据查询内容选择数据源 → 分析 → 报告     │
└─────────────────────────────────────────────────────────────┘
```

## 详细场景分析

### 场景1：无CSV + 有数据库 (DATABASE_ONLY)
```
用户查询 → QueryRewriteNode → 数据源检测 → DATABASE_ONLY
    ↓
PlannerNode → 生成数据库分析计划
    ↓
PlanExecutorNode → 执行数据库分析流程
    ↓
┌─────────────────────────────────────────────────────────────┐
│                   数据库分析流程                             │
├─────────────────────────────────────────────────────────────┤
│ 1. KeywordExtractNode (关键词提取)                        │
│ 2. TableRelationNode (表关系分析)                         │
│ 3. NL2SQLNode (自然语言转SQL)                            │
│ 4. DatabaseQueryNode (数据库查询)                        │
│ 5. PythonAnalyzeNode (Python分析代码生成)                │
│ 6. PythonExecuteNode (Python代码执行)                    │
│ 7. ReportGeneratorNode (报告生成)                        │
└─────────────────────────────────────────────────────────────┘
```

### 场景2：有CSV + 无数据库 (CSV_ONLY)
```
用户查询 → QueryRewriteNode → 数据源检测 → CSV_ONLY
    ↓
PlannerNode → 生成CSV分析计划
    ↓
PlanExecutorNode → 执行CSV分析流程
    ↓
┌─────────────────────────────────────────────────────────────┐
│                    CSV分析流程                              │
├─────────────────────────────────────────────────────────────┤
│ 1. CsvSchemaNode (CSV结构解析)                           │
│ 2. CsvAnalyzeNode (CSV分析代码生成)                      │
│ 3. PythonExecuteNode (Python代码执行)                    │
│ 4. ReportGeneratorNode (报告生成)                        │
└─────────────────────────────────────────────────────────────┘
```

### 场景3：有CSV + 有数据库 (MIXED_SOURCE)
```
用户查询 → QueryRewriteNode → 数据源检测 → MIXED_SOURCE
    ↓
PlannerNode → 智能路由分析
    ↓
┌─────────────────────────────────────────────────────────────┐
│                   智能路由判断                              │
├─────────────────────────────────────────────────────────────┤
│ 查询内容分析 → 判断主要数据源 → 选择执行路径               │
│                                                           │
│ 如果查询主要涉及CSV数据 → 走CSV分析路径                   │
│ 如果查询主要涉及数据库数据 → 走数据库分析路径             │
│ 如果查询涉及两种数据源 → 走混合分析路径                   │
└─────────────────────────────────────────────────────────────┘
    ↓
PlanExecutorNode → 执行选定的分析流程
```

## 核心节点设计

### 1. 数据源检测节点 (DataSourceDetectNode)
```java
public class DataSourceDetectNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 获取会话中的CSV文件
        String sessionId = state.value(SESSION_ID).orElseThrow();
        List<CsvFile> csvFiles = csvFileService.getBySessionId(sessionId);
        
        // 2. 检查数据库配置
        Integer agentId = state.value(AGENT_ID).orElseThrow();
        Agent agent = agentService.findById(agentId.longValue());
        boolean hasDatabase = agent.getDatabaseConfig() != null;
        
        // 3. 判断数据源类型
        String dataSourceType = determineDataSourceType(csvFiles, hasDatabase);
        
        return Map.of(
            DATA_SOURCE_TYPE, dataSourceType,
            CSV_FILES, csvFiles,
            HAS_DATABASE, hasDatabase
        );
    }
    
    private String determineDataSourceType(List<CsvFile> csvFiles, boolean hasDatabase) {
        if (csvFiles.isEmpty() && hasDatabase) {
            return "DATABASE_ONLY";
        } else if (!csvFiles.isEmpty() && !hasDatabase) {
            return "CSV_ONLY";
        } else if (!csvFiles.isEmpty() && hasDatabase) {
            return "MIXED_SOURCE";
        } else {
            return "NO_DATA_SOURCE";
        }
    }
}
```

### 2. CSV结构解析节点 (CsvSchemaNode)
```java
public class CsvSchemaNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        List<CsvFile> csvFiles = state.value(CSV_FILES, List.class);
        
        // 解析CSV文件结构
        List<CsvSchema> schemas = new ArrayList<>();
        for (CsvFile csvFile : csvFiles) {
            CsvSchema schema = parseCsvFile(csvFile);
            schemas.add(schema);
        }
        
        return Map.of(
            CSV_SCHEMAS, schemas,
            CSV_SCHEMA_COUNT, schemas.size()
        );
    }
    
    private CsvSchema parseCsvFile(CsvFile csvFile) {
        // 1. 从S3下载文件
        byte[] fileContent = s3FileUploadService.downloadFile(csvFile.getFilePath());
        
        // 2. 解析CSV结构
        String csvContent = parseCsvContent(fileContent);
        String[] lines = csvContent.split("\n");
        
        // 3. 构建schema
        CsvSchema schema = new CsvSchema();
        schema.setFileName(csvFile.getOriginalFilename());
        schema.setHeaders(parseHeaders(lines[0]));
        schema.setDataTypes(inferDataTypes(lines));
        schema.setRowCount(lines.length - 1);
        
        return schema;
    }
}
```

### 3. CSV分析节点 (CsvAnalyzeNode)
```java
public class CsvAnalyzeNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String query = state.value(INPUT_KEY).orElseThrow();
        List<CsvSchema> schemas = state.value(CSV_SCHEMAS, List.class);
        
        // 生成Python分析代码
        String pythonCode = generateCsvAnalysisCode(query, schemas);
        
        return Map.of(
            CSV_ANALYSIS_CODE, pythonCode,
            ANALYSIS_TYPE, "CSV_ANALYSIS"
        );
    }
    
    private String generateCsvAnalysisCode(String query, List<CsvSchema> schemas) {
        // 1. 构建CSV文件路径
        StringBuilder filePaths = new StringBuilder();
        for (CsvSchema schema : schemas) {
            filePaths.append("'").append(schema.getFileName()).append("', ");
        }
        
        // 2. 生成Python代码模板
        String template = """
            import pandas as pd
            import numpy as np
            import matplotlib.pyplot as plt
            import seaborn as sns
            
            # 读取CSV文件
            files = [%s]
            dataframes = {}
            for file in files:
                df = pd.read_csv(file)
                dataframes[file] = df
                print(f"文件 {file} 包含 {len(df)} 行数据")
                print(f"列名: {list(df.columns)}")
                print("前5行数据:")
                print(df.head())
                print("\\n")
            
            # 用户查询: %s
            # 请根据用户查询进行数据分析
            """;
        
        return String.format(template, filePaths.toString(), query);
    }
}
```

### 4. 智能路由节点 (SmartRouterNode)
```java
public class SmartRouterNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String query = state.value(INPUT_KEY).orElseThrow();
        List<CsvSchema> csvSchemas = state.value(CSV_SCHEMAS, List.class);
        SchemaDTO databaseSchema = state.value(TABLE_RELATION_OUTPUT, SchemaDTO.class);
        
        // 分析查询意图
        QueryIntent intent = analyzeQueryIntent(query, csvSchemas, databaseSchema);
        
        return Map.of(
            QUERY_INTENT, intent,
            ROUTING_DECISION, intent.getRecommendedPath()
        );
    }
    
    private QueryIntent analyzeQueryIntent(String query, List<CsvSchema> csvSchemas, SchemaDTO databaseSchema) {
        QueryIntent intent = new QueryIntent();
        
        // 1. 检查查询中是否包含CSV相关的关键词
        boolean hasCsvKeywords = checkCsvKeywords(query, csvSchemas);
        
        // 2. 检查查询中是否包含数据库相关的关键词
        boolean hasDbKeywords = checkDatabaseKeywords(query, databaseSchema);
        
        // 3. 根据关键词匹配度决定路由
        if (hasCsvKeywords && !hasDbKeywords) {
            intent.setRecommendedPath("CSV_ONLY");
        } else if (!hasCsvKeywords && hasDbKeywords) {
            intent.setRecommendedPath("DATABASE_ONLY");
        } else if (hasCsvKeywords && hasDbKeywords) {
            intent.setRecommendedPath("MIXED_ANALYSIS");
        } else {
            // 默认使用CSV（如果存在）
            intent.setRecommendedPath(csvSchemas.isEmpty() ? "DATABASE_ONLY" : "CSV_ONLY");
        }
        
        return intent;
    }
}
```

## 实现优先级

### 第一阶段：基础CSV支持
1. 实现DataSourceDetectNode
2. 实现CsvSchemaNode
3. 实现CsvAnalyzeNode
4. 修改PlannerNode支持CSV计划生成

### 第二阶段：智能路由
1. 实现SmartRouterNode
2. 完善混合数据源分析
3. 优化查询意图识别

### 第三阶段：性能优化
1. CSV文件缓存机制
2. 分析结果缓存
3. 并发处理优化

## 技术要点

### 1. CSV文件处理
- 使用pandas进行CSV解析
- 支持多种编码格式（UTF-8, GBK, GB2312）
- 自动推断数据类型
- 处理大文件分块读取

### 2. 查询意图识别
- 基于关键词匹配
- 结合schema信息进行智能判断
- 支持模糊匹配和语义理解

### 3. 代码生成
- 基于模板生成Python代码
- 支持多种分析场景
- 自动导入必要的库
- 错误处理和异常捕获

### 4. 结果展示
- 统一的报告格式
- 支持图表和表格展示
- 可下载的分析结果
- 交互式数据探索
