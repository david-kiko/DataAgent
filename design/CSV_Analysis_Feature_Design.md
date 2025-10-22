# CSV分析功能设计方案

## 概述

本文档描述了在现有DataAgent项目中增加CSV文件分析功能的详细设计方案。该方案采用渐进式扩展的方式，在保持现有架构稳定性的基础上，为智能体增加CSV文件上传和分析能力。

## 设计原则

- **最小侵入性**：不修改现有核心逻辑
- **用户友好**：提供拖拽上传等良好的用户体验
- **技术复用**：借鉴joyagent-jdgenie项目的成熟CSV解析逻辑
- **数据隔离**：每个智能体独立管理自己的CSV文件
- **配置灵活**：支持按智能体配置CSV功能开关

## 架构设计

### 整体架构

```
用户上传CSV → 文件验证 → 存储到指定目录 → 数据库记录元数据 → 聊天时触发分析 → 返回分析结果
```

### 核心组件

1. **Agent配置扩展**：增加CSV支持开关
2. **文件上传服务**：处理CSV文件上传和存储
3. **CSV分析引擎**：借鉴joyagent-jdgenie的解析逻辑
4. **聊天集成**：在对话中集成CSV分析功能

## 详细实现方案

### 1. 数据库设计

#### Agent表扩展

```sql
-- 在现有agent表中增加CSV相关字段
ALTER TABLE agent ADD COLUMN csv_upload_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE agent ADD COLUMN csv_storage_path VARCHAR(500);
ALTER TABLE agent ADD COLUMN csv_max_file_size BIGINT DEFAULT 10485760; -- 10MB
```

#### CSV文件表

```sql
-- CSV文件表
CREATE TABLE csv_files (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    agent_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    schema_info TEXT,  -- JSON格式的CSV结构信息
    FOREIGN KEY (agent_id) REFERENCES agent(id)
);

-- CSV分析结果表
CREATE TABLE csv_analysis_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    csv_file_id BIGINT NOT NULL,
    query_text TEXT NOT NULL,
    analysis_result TEXT,  -- JSON格式的分析结果
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (csv_file_id) REFERENCES csv_files(id)
);
```

### 2. 后端实现

#### Agent实体扩展

```java
@Entity
public class Agent {
    // 现有字段...
    
    // 新增CSV支持配置
    @Column(name = "csv_upload_enabled")
    private Boolean csvUploadEnabled = false;
    
    @Column(name = "csv_storage_path")
    private String csvStoragePath;
    
    @Column(name = "csv_max_file_size")
    private Long csvMaxFileSize = 10L * 1024 * 1024; // 默认10MB
    
    // 关联的CSV文件
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL)
    private List<CsvFile> csvFiles;
}
```

#### CSV文件实体

```java
@Entity
@Table(name = "csv_files")
public class CsvFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "agent_id")
    private Long agentId;
    
    @Column(name = "original_filename")
    private String originalFilename;
    
    @Column(name = "stored_filename")
    private String storedFilename;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "upload_time")
    private LocalDateTime uploadTime;
    
    @Column(name = "status")
    private String status = "ACTIVE";
    
    @Column(name = "schema_info")
    private String schemaInfo; // JSON格式的CSV结构信息
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", insertable = false, updatable = false)
    private Agent agent;
}
```

#### CSV上传控制器

```java
@RestController
@RequestMapping("/api/csv")
@CrossOrigin(origins = "*")
public class CsvUploadController {
    
    @Autowired
    private AgentService agentService;
    
    @Autowired
    private CsvFileService csvFileService;
    
    @PostMapping("/upload")
    public ResponseEntity<CsvUploadResponse> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("agentId") Long agentId) {
        
        // 1. 验证Agent是否支持CSV上传
        Agent agent = agentService.findById(agentId);
        if (agent == null || !Boolean.TRUE.equals(agent.getCsvUploadEnabled())) {
            return ResponseEntity.badRequest()
                .body(CsvUploadResponse.error("该智能体不支持CSV文件上传"));
        }
        
        // 2. 验证文件类型和大小
        if (!isValidCsvFile(file, agent)) {
            return ResponseEntity.badRequest()
                .body(CsvUploadResponse.error("文件类型或大小不符合要求"));
        }
        
        // 3. 保存文件并记录到数据库
        CsvFile csvFile = csvFileService.saveCsvFile(file, agentId);
        
        return ResponseEntity.ok(CsvUploadResponse.success(csvFile));
    }
    
    private boolean isValidCsvFile(MultipartFile file, Agent agent) {
        // 检查文件大小
        if (file.getSize() > agent.getCsvMaxFileSize()) {
            return false;
        }
        
        // 检查文件类型
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        
        return (contentType != null && contentType.contains("text/csv")) ||
               (filename != null && filename.toLowerCase().endsWith(".csv"));
    }
}
```

#### CSV分析服务

```java
@Service
public class CsvAnalysisService {
    
    // 借鉴joyagent-jdgenie的CSV解析逻辑
    public CsvAnalysisResult analyzeCsv(String filePath, String query) {
        try {
            // 1. 读取CSV文件
            DataFrame df = readCsvFile(filePath);
            
            // 2. 分析文件结构
            CsvSchema schema = analyzeCsvSchema(df);
            
            // 3. 自然语言转CSV查询（借鉴genie-tool的逻辑）
            String csvQuery = nl2CsvQuery(query, schema);
            
            // 4. 执行查询
            QueryResult result = executeCsvQuery(csvQuery, df);
            
            // 5. 生成分析结果
            return generateAnalysisResult(result, schema);
            
        } catch (Exception e) {
            log.error("CSV分析失败", e);
            throw new CsvAnalysisException("CSV文件分析失败: " + e.getMessage());
        }
    }
    
    private DataFrame readCsvFile(String filePath) {
        // 使用pandas或类似库读取CSV
        // 这里可以调用Python脚本或使用Java CSV库
        return CsvReader.read(filePath);
    }
    
    private String nl2CsvQuery(String naturalLanguage, CsvSchema schema) {
        // 借鉴joyagent-jdgenie的NL2CSV逻辑
        // 将自然语言转换为CSV查询条件
        return CsvQueryTranslator.translate(naturalLanguage, schema);
    }
}
```

#### 聊天接口集成

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    @Autowired
    private CsvAnalysisService csvAnalysisService;
    
    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        
        // 1. 检查是否涉及CSV文件
        List<CsvFile> csvFiles = getCsvFilesForAgent(request.getAgentId());
        
        if (!csvFiles.isEmpty() && containsCsvQuery(request.getMessage())) {
            // 2. 执行CSV分析
            CsvAnalysisResult result = csvAnalysisService.analyzeCsv(
                csvFiles.get(0).getFilePath(), 
                request.getMessage()
            );
            
            // 3. 返回分析结果
            return ResponseEntity.ok(ChatResponse.csvAnalysis(result));
        }
        
        // 4. 否则执行原有的NL2SQL逻辑
        return executeNl2Sql(request);
    }
}
```

### 3. 前端实现

#### Agent配置页面扩展

```typescript
// Agent配置页面新增CSV选项
const AgentConfigForm = () => {
  const [form] = Form.useForm();
  const [csvEnabled, setCsvEnabled] = useState(false);
  
  return (
    <Form form={form}>
      {/* 现有配置... */}
      
      {/* CSV支持配置 */}
      <Card title="CSV文件支持" style={{ marginTop: 16 }}>
        <Form.Item name="csvUploadEnabled" valuePropName="checked">
          <Switch 
            checkedChildren="启用" 
            unCheckedChildren="禁用"
            onChange={setCsvEnabled}
          />
        </Form.Item>
        
        {csvEnabled && (
          <>
            <Form.Item label="最大文件大小(MB)" name="csvMaxFileSize">
              <InputNumber min={1} max={100} defaultValue={10} />
            </Form.Item>
            
            <Form.Item label="允许的文件类型" name="csvAllowedTypes">
              <Select mode="multiple" defaultValue={['csv', 'xlsx']}>
                <Option value="csv">CSV</Option>
                <Option value="xlsx">Excel</Option>
                <Option value="xls">Excel 97-2003</Option>
              </Select>
            </Form.Item>
          </>
        )}
      </Card>
    </Form>
  );
};
```

#### 聊天界面文件上传

```typescript
// 聊天界面新增文件上传功能
const ChatInterface = ({ agent }) => {
  const [uploading, setUploading] = useState(false);
  const [uploadedFiles, setUploadedFiles] = useState([]);
  
  // 拖拽上传处理
  const handleDrop = useCallback((e) => {
    e.preventDefault();
    if (!agent.csvUploadEnabled) return;
    
    const files = Array.from(e.dataTransfer.files);
    handleFileUpload(files);
  }, [agent.csvUploadEnabled]);
  
  // 文件上传处理
  const handleFileUpload = async (files) => {
    setUploading(true);
    try {
      const uploadPromises = files.map(file => uploadCsvFile(file, agent.id));
      const results = await Promise.all(uploadPromises);
      setUploadedFiles(prev => [...prev, ...results]);
    } finally {
      setUploading(false);
    }
  };
  
  return (
    <div 
      className="chat-container"
      onDrop={handleDrop}
      onDragOver={(e) => e.preventDefault()}
    >
      {/* 聊天消息区域 */}
      <div className="messages">
        {/* 现有消息显示... */}
      </div>
      
      {/* 文件上传区域 */}
      {agent.csvUploadEnabled && (
        <div className="file-upload-area">
          <Upload.Dragger
            multiple
            accept=".csv,.xlsx,.xls"
            beforeUpload={() => false}
            onChange={handleFileUpload}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">
              点击或拖拽CSV文件到此区域上传
            </p>
          </Upload.Dragger>
        </div>
      )}
      
      {/* 输入框 */}
      <div className="input-area">
        {/* 现有输入框... */}
      </div>
    </div>
  );
};
```

### 4. 部署配置

#### Docker配置扩展

```yaml
# docker-compose.yml
backend:
  volumes:
    - uploads-data:/app/uploads
    - csv-data:/app/uploads/csv        # CSV文件存储
    - python-scripts:/app/scripts      # Python分析脚本
  environment:
    - CSV_ANALYSIS_ENABLED=true
    - PYTHON_SCRIPT_PATH=/app/scripts

volumes:
  uploads-data:
  csv-data:                            # CSV文件数据卷
  python-scripts:                      # Python脚本数据卷
```

#### 应用配置扩展

```yaml
# application.yml
spring:
  ai:
    alibaba:
      nl2sql:
        file:
          upload:
            path: ./uploads
            url-prefix: /uploads
            imageSize: 2097152
            # 新增CSV相关配置
            csv:
              max-file-size: 100MB      # CSV文件大小限制
              allowed-types: [csv, xlsx]  # 允许的文件类型
              storage-path: ./uploads/csv
              cache-path: ./cache/csv-analysis
```

### 5. 文件存储结构

```
uploads/
├── avatars/           # 头像文件
│   └── uuid.jpg
├── csv/              # CSV文件
│   ├── agent_1/
│   │   ├── sales_data_20240101.csv
│   │   └── user_data_20240102.csv
│   └── agent_2/
│       └── report_20240101.csv
├── cache/            # 分析结果缓存
│   ├── schema/       # 文件结构缓存
│   ├── index/        # 索引文件
│   └── results/      # 分析结果
└── temp/             # 临时文件
    └── processing_xxx.csv
```

### 6. 技术实现细节

#### 文件命名策略

```java
// 包含更多信息的命名
String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
String filename = "csv_" + agentId + "_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
// 示例：csv_1_20240115_143022_a1b2c3d4.csv
```

#### 访问URL配置

```
文件访问URL：http://localhost:8065/uploads/csv/csv_1_20240115_143022_a1b2c3d4.csv
分析结果URL：http://localhost:8065/api/analysis/csv/{fileId}/result
```

#### 清理策略

```java
@Component
public class FileCleanupService {
    
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void cleanupExpiredFiles() {
        // 清理超过30天的临时文件
        // 清理超过7天的分析缓存
        // 清理无效的CSV文件
    }
}
```

## 实施计划

### 第一阶段：基础功能
1. 扩展Agent实体，增加CSV配置字段
2. 实现CSV文件上传接口
3. 前端配置页面增加CSV开关

### 第二阶段：文件管理
1. 实现文件存储和元数据管理
2. 前端聊天界面增加文件上传功能
3. 文件列表和删除功能

### 第三阶段：分析引擎
1. 集成joyagent-jdgenie的CSV解析逻辑
2. 实现自然语言转CSV查询
3. 聊天接口集成CSV分析

### 第四阶段：优化完善
1. 性能优化和缓存机制
2. 错误处理和用户提示
3. 安全性和权限控制

## 优势分析

1. **渐进式扩展**：不影响现有功能
2. **用户友好**：拖拽上传体验好
3. **技术复用**：直接借鉴成熟方案
4. **配置灵活**：每个智能体独立配置
5. **数据隔离**：文件按智能体分类存储
6. **可扩展性**：未来可以轻松添加Excel、JSON等数据源

## 风险评估

1. **技术风险**：CSV解析逻辑的集成复杂度
2. **性能风险**：大文件处理可能影响系统性能
3. **安全风险**：文件上传需要严格的安全控制
4. **存储风险**：文件存储空间管理

## 总结

该方案通过最小侵入的方式为DataAgent项目增加了CSV分析功能，既保持了现有架构的稳定性，又提供了强大的数据分析能力。通过借鉴joyagent-jdgenie项目的成熟技术，可以快速实现功能并保证质量。
