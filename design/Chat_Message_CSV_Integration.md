# Chat Message 表集成CSV分析结果

## 🎯 设计理念

使用现有的 `chat_message` 表来存储CSV分析结果，避免创建冗余的 `csv_analysis_results` 表。

## 📋 表结构设计

### chat_message 表字段映射

| 原 csv_analysis_results 字段 | chat_message 字段 | 说明 |
|------------------------------|-------------------|------|
| `query_text` | `content` (user消息) | 用户查询文本作为用户消息存储 |
| `analysis_result` | `content` (assistant消息) | 分析结果作为助手回复存储 |
| `execution_time` | `metadata.execution_time` | 执行时间存储在metadata中 |
| `status` | `metadata.status` | 执行状态存储在metadata中 |
| `error_message` | `content` (error消息) | 错误信息作为错误消息存储 |
| `csv_file_id` | `metadata.csv_file_id` | CSV文件ID存储在metadata中 |

## 🔄 业务流程

### 1. CSV分析流程

```
用户上传CSV文件 → 用户提问 → 系统分析 → 存储结果到chat_message表
```

### 2. 消息存储示例

#### 用户查询消息
```json
{
  "session_id": "session123",
  "role": "user",
  "content": "分析这个CSV文件的销售趋势",
  "message_type": "text",
  "metadata": {
    "csv_file_id": 1,
    "query_type": "csv_analysis"
  }
}
```

#### 分析结果消息
```json
{
  "session_id": "session123",
  "role": "assistant",
  "content": "{\"chart_data\": {...}, \"statistics\": {...}}",
  "message_type": "csv_analysis",
  "metadata": {
    "csv_file_id": 1,
    "execution_time": 1500,
    "status": "SUCCESS",
    "analysis_type": "trend_analysis"
  }
}
```

#### 错误消息
```json
{
  "session_id": "session123",
  "role": "assistant",
  "content": "分析失败：文件格式不支持",
  "message_type": "error",
  "metadata": {
    "csv_file_id": 1,
    "execution_time": 200,
    "status": "FAILED",
    "error_code": "INVALID_FORMAT"
  }
}
```

## 💻 代码实现

### 1. 存储CSV分析结果

```java
// 存储用户查询
ChatMessage userMessage = ChatMessage.builder()
    .sessionId(sessionId)
    .role("user")
    .content(queryText)
    .messageType("text")
    .metadata(Map.of(
        "csv_file_id", csvFileId,
        "query_type", "csv_analysis"
    ))
    .build();

// 存储分析结果
ChatMessage analysisMessage = ChatMessage.builder()
    .sessionId(sessionId)
    .role("assistant")
    .content(analysisResultJson)
    .messageType("csv_analysis")
    .metadata(Map.of(
        "csv_file_id", csvFileId,
        "execution_time", executionTime,
        "status", "SUCCESS",
        "analysis_type", analysisType
    ))
    .build();
```

### 2. 查询CSV分析历史

```java
// 查询会话中的所有CSV分析结果
List<ChatMessage> csvAnalyses = chatMessageMapper.selectBySessionIdAndMessageType(
    sessionId, "csv_analysis"
);

// 查询特定CSV文件的分析历史
List<ChatMessage> fileAnalyses = chatMessageMapper.selectByCsvFileId(csvFileId);
```

### 3. 统计信息查询

```sql
-- 查询CSV分析统计
SELECT 
    COUNT(*) as total_analyses,
    AVG(JSON_EXTRACT(metadata, '$.execution_time')) as avg_execution_time,
    SUM(CASE WHEN JSON_EXTRACT(metadata, '$.status') = 'SUCCESS' THEN 1 ELSE 0 END) as success_count
FROM chat_message 
WHERE message_type = 'csv_analysis' 
AND session_id = ?;
```

## 🎨 前端展示

### 1. 消息类型识别

```javascript
// 识别CSV分析消息
const isCsvAnalysis = (message) => {
  return message.message_type === 'csv_analysis';
};

// 渲染CSV分析结果
const renderCsvAnalysis = (message) => {
  const analysisData = JSON.parse(message.content);
  const metadata = message.metadata;
  
  return {
    chartData: analysisData.chart_data,
    statistics: analysisData.statistics,
    executionTime: metadata.execution_time,
    status: metadata.status
  };
};
```

### 2. 历史记录展示

```javascript
// 获取CSV分析历史
const getCsvAnalysisHistory = (sessionId) => {
  return messages.filter(msg => 
    msg.message_type === 'csv_analysis' && 
    msg.session_id === sessionId
  );
};
```

## ✅ 优势

### 1. **数据一致性**
- 所有对话内容统一存储在chat_message表中
- 避免数据分散和同步问题

### 2. **简化架构**
- 减少表数量，降低复杂度
- 统一的查询接口

### 3. **扩展性好**
- 通过message_type区分不同类型的消息
- metadata字段支持灵活的元数据存储

### 4. **历史追踪**
- 完整的对话历史，包括CSV分析过程
- 支持按时间线查看分析结果

## 🔧 迁移方案

### 1. 现有数据迁移
如果有现有的csv_analysis_results数据，可以迁移到chat_message表：

```sql
INSERT INTO chat_message (session_id, role, content, message_type, metadata, create_time)
SELECT 
    session_id,
    'assistant',
    analysis_result,
    'csv_analysis',
    JSON_OBJECT(
        'csv_file_id', csv_file_id,
        'execution_time', execution_time,
        'status', status,
        'original_query', query_text
    ),
    created_time
FROM csv_analysis_results;
```

### 2. 代码适配
- 更新CsvFileService，使用ChatMessage存储分析结果
- 修改前端代码，识别csv_analysis消息类型
- 更新API接口，返回统一的消息格式

这样的设计更加简洁和统一，避免了表冗余的问题！

