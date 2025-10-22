# S3存储配置指南

## 问题分析

你遇到的错误 `Your account is not signed up2 (Service: S3, Status Code: 401)` 是因为：

1. **端口9010是Apache Doris的FE服务**，不是S3存储服务
2. **Doris不支持S3协议**，所以返回了"账户未注册"的错误
3. **需要部署真正的S3兼容存储服务**

## 解决方案

### 方案1：使用MinIO（推荐）

MinIO是一个高性能的S3兼容对象存储服务，适合本地部署。

#### 1. 启动MinIO服务

```bash
# 停止现有服务
docker-compose down

# 启动包含MinIO的服务
docker-compose up -d minio

# 检查MinIO状态
docker-compose ps minio
```

#### 2. 访问MinIO控制台

- 控制台地址：http://localhost:9001
- 用户名：minioadmin
- 密码：minioadmin123

#### 3. 创建存储桶

在MinIO控制台中创建名为 `dataagent` 的存储桶。

#### 4. 测试连接

```bash
python test_minio_connection.py
```

### 方案2：使用其他S3兼容服务

如果你有其他S3兼容服务（如AWS S3、阿里云OSS等），可以修改配置：

```yaml
# 在docker-compose.yml中修改
environment:
  - S3_ENDPOINT=https://your-s3-endpoint.com
  - S3_ACCESS_KEY=your-access-key
  - S3_SECRET_KEY=your-secret-key
  - S3_BUCKET_NAME=dataagent
  - S3_REGION=us-east-1
```

## 配置说明

### MinIO配置

```yaml
# MinIO服务配置
minio:
  image: minio/minio:latest
  container_name: data-agent-minio
  environment:
    - MINIO_ROOT_USER=minioadmin
    - MINIO_ROOT_PASSWORD=minioadmin123
  ports:
    - "9000:9000"   # API端口
    - "9001:9001"   # 控制台端口
  volumes:
    - minio-data:/data
  command: server /data --console-address ":9001"
```

### 后端服务配置

```yaml
# 后端服务S3配置
environment:
  - S3_ENDPOINT=http://minio:9000
  - S3_ACCESS_KEY=minioadmin
  - S3_SECRET_KEY=minioadmin123
  - S3_BUCKET_NAME=dataagent
  - S3_REGION=us-east-1
```

## 验证步骤

1. **启动服务**：
   ```bash
   docker-compose up -d
   ```

2. **检查服务状态**：
   ```bash
   docker-compose ps
   ```

3. **测试S3连接**：
   ```bash
   python test_minio_connection.py
   ```

4. **访问MinIO控制台**：
   打开 http://localhost:9001 验证存储桶是否创建

5. **测试文件上传**：
   在前端界面尝试上传CSV文件

## 故障排除

### 常见问题

1. **MinIO服务无法启动**
   - 检查端口9000、9001是否被占用
   - 查看MinIO容器日志：`docker-compose logs minio`

2. **连接被拒绝**
   - 确保MinIO服务正在运行
   - 检查网络连接
   - 验证端点URL是否正确

3. **认证失败**
   - 检查Access Key和Secret Key是否正确
   - 确保MinIO服务已完全启动

4. **存储桶不存在**
   - 在MinIO控制台中手动创建存储桶
   - 或者让程序自动创建（需要相应权限）

### 日志查看

```bash
# 查看MinIO日志
docker-compose logs minio

# 查看后端服务日志
docker-compose logs backend

# 实时查看日志
docker-compose logs -f backend
```

## 总结

通过部署MinIO服务，你可以获得一个完整的S3兼容存储解决方案，支持：

- ✅ 文件上传和下载
- ✅ 存储桶管理
- ✅ 访问控制
- ✅ 与现有代码完全兼容

这样就能解决你遇到的S3认证问题了。
