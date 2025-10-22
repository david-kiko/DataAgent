# S3配置总结

## 配置文件更新

### 1. application.yml
在 `spring-ai-alibaba-data-agent-management/src/main/resources/application.yml` 中添加了S3配置：

```yaml
# S3存储配置
s3:
  endpoint: ${S3_ENDPOINT:http://192.168.30.132:9010}
  access-key: ${S3_ACCESS_KEY:1tdvQZ5Er0CUSckmLsl7}
  secret-key: ${S3_SECRET_KEY:Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0}
  bucket-name: ${S3_BUCKET_NAME:dataagent}
  # region: ${S3_REGION:us-east-1}  # MinIO等S3兼容存储通常不需要region
```

### 2. docker-compose.yml
在 `docker-file/docker-compose.yml` 的backend服务中添加了S3环境变量：

```yaml
environment:
  # 现有配置...
  # S3存储配置
  - S3_ENDPOINT=http://192.168.30.132:9010
  - S3_ACCESS_KEY=1tdvQZ5Er0CUSckmLsl7
  - S3_SECRET_KEY=Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0
  - S3_BUCKET_NAME=dataagent
  # - S3_REGION=us-east-1  # MinIO等S3兼容存储通常不需要region
```

## 代码更新

### 1. S3Config配置类
创建了 `com.alibaba.cloud.ai.config.S3Config` 配置类：

```java
@Data
@Component
@ConfigurationProperties(prefix = "s3")
public class S3Config {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String region;  // 可选，MinIO等S3兼容存储通常不需要
}
```

### 2. S3FileUploadService更新
- 移除了硬编码的配置值
- 使用 `@Autowired` 注入 `S3Config`
- 通过配置类获取S3连接参数

## 配置说明

### 环境变量支持
所有S3配置都支持通过环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| S3_ENDPOINT | http://192.168.30.132:9010 | S3服务端点 |
| S3_ACCESS_KEY | 1tdvQZ5Er0CUSckmLsl7 | 访问密钥 |
| S3_SECRET_KEY | Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0 | 秘密密钥 |
| S3_BUCKET_NAME | dataagent | 存储桶名称 |
| S3_REGION | 无 | 区域（可选，MinIO等S3兼容存储通常不需要） |

### 部署方式

#### 1. 本地开发
直接使用 `application.yml` 中的默认配置即可。

#### 2. Docker部署
通过 `docker-compose.yml` 中的环境变量配置。

#### 3. 生产环境
可以通过以下方式配置：

**方式1：环境变量**
```bash
export S3_ENDPOINT=http://your-s3-endpoint:9000
export S3_ACCESS_KEY=your-access-key
export S3_SECRET_KEY=your-secret-key
export S3_BUCKET_NAME=your-bucket
export S3_REGION=us-east-1
```

**方式2：JVM参数**
```bash
java -jar app.jar \
  -Ds3.endpoint=http://your-s3-endpoint:9000 \
  -Ds3.access-key=your-access-key \
  -Ds3.secret-key=your-secret-key \
  -Ds3.bucket-name=your-bucket \
  -Ds3.region=us-east-1
```

**方式3：外部配置文件**
```yaml
# application-prod.yml
s3:
  endpoint: http://your-s3-endpoint:9000
  access-key: your-access-key
  secret-key: your-secret-key
  bucket-name: your-bucket
  region: us-east-1
```

## 安全注意事项

1. **密钥管理**：生产环境中应使用安全的密钥管理方案，避免硬编码
2. **网络安全**：确保S3服务端点的网络访问安全
3. **权限控制**：配置适当的S3访问权限
4. **加密传输**：建议使用HTTPS端点

## 测试验证

### 1. 配置验证
启动应用后，检查日志中是否有S3连接相关的错误信息。

### 2. 功能测试
- 上传CSV文件
- 检查文件是否成功存储到S3
- 验证文件路径和元数据

### 3. 连接测试
可以通过以下方式测试S3连接：

```java
@Autowired
private S3FileUploadService s3Service;

// 测试连接
s3Service.testConnection();
```

## 故障排除

### 常见问题

1. **连接超时**
   - 检查网络连接
   - 验证endpoint地址

2. **认证失败**
   - 检查access-key和secret-key
   - 验证权限配置

3. **存储桶不存在**
   - 检查bucket-name配置
   - 确认存储桶已创建

4. **区域不匹配**
   - 检查region配置
   - 确认与S3服务区域一致
