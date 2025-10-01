# Apache Doris Docker Compose 配置

这个配置文件用于启动Apache Doris集群，包含FE（Frontend）和BE（Backend）服务，并配置了数据持久化。

## 功能特性

- **数据持久化**: 使用Docker volumes确保数据在容器重启后不丢失
- **健康检查**: 自动监控服务状态
- **网络隔离**: 使用自定义网络确保服务间通信安全
- **自动重启**: 容器异常退出时自动重启

## 持久化存储

### FE (Frontend) 持久化
- **元数据**: `/opt/apache-doris/fe/doris-meta` - 存储集群元数据
- **日志**: `/opt/apache-doris/fe/log` - 存储FE日志文件

### BE (Backend) 持久化
- **存储数据**: `/opt/apache-doris/be/storage` - 存储实际数据文件
- **日志**: `/opt/apache-doris/be/log` - 存储BE日志文件

## 使用方法

### 1. 启动集群
```bash
cd docker-file
docker-compose -f docker-compose-doris.yml up -d
```

### 2. 查看服务状态
```bash
docker-compose -f docker-compose-doris.yml ps
```

### 3. 查看日志
```bash
# 查看所有服务日志
docker-compose -f docker-compose-doris.yml logs -f

# 查看特定服务日志
docker-compose -f docker-compose-doris.yml logs -f fe
docker-compose -f docker-compose-doris.yml logs -f be
```

### 4. 停止集群
```bash
docker-compose -f docker-compose-doris.yml down
```

### 5. 完全清理（包括数据）
```bash
docker-compose -f docker-compose-doris.yml down -v
```

## 访问地址

- **FE Web UI**: http://localhost:8030
- **BE Web UI**: http://localhost:8040
- **MySQL连接**: `mysql -uroot -p -P9030 -h127.0.0.1` (密码: doris123456)

## 默认配置

- **Doris版本**: 3.0.4
- **Root密码**: doris123456
- **FE端口**: 8030 (HTTP), 9030 (MySQL), 9010 (RPC)
- **BE端口**: 8040 (HTTP), 9050 (RPC)
- **网络**: 172.30.0.0/16

## 注意事项

1. 首次启动可能需要几分钟时间来初始化服务
2. 确保端口8030、8040、9030、9010、9050没有被其他服务占用
3. 数据持久化使用Docker volumes，数据存储在Docker的默认位置
4. 如需修改版本，请同时更新FE和BE的镜像标签
5. **安全提醒**: 默认root密码为`doris123456`，生产环境请修改为强密码

## 故障排除

### 网络冲突问题
如果遇到 "Pool overlaps with other one on this address space" 错误：

1. **清理现有网络**：
```bash
docker network prune
```

2. **查看现有网络**：
```bash
docker network ls
```

3. **如果仍有冲突，修改网络配置**：
编辑 `docker-compose-doris.yml` 文件，将网络段改为其他地址，如：
```yaml
networks:
  doris_network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.31.0.0/16  # 或其他未使用的网段
```

### 查看服务健康状态
```bash
docker-compose -f docker-compose-doris.yml ps
```

### 进入容器调试
```bash
# 进入FE容器
docker exec -it doris-fe bash

# 进入BE容器
docker exec -it doris-be bash
```

### 重置集群
如果遇到问题需要重置：
```bash
docker-compose -f docker-compose-doris.yml down -v
docker-compose -f docker-compose-doris.yml up -d
```
