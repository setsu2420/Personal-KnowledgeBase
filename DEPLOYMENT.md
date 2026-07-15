# Docker 部署指南

## 快速开始

### 前置要求
- Docker 20.10+
- Docker Compose 2.0+

### 一键启动

```bash
# 1. 克隆项目
git clone <repository-url>
cd 范_副本

# 2. 设置环境变量（可选）
export MYSQL_ROOT_PASSWORD=your_secure_password

# 3. 构建并启动所有服务
docker-compose up -d

# 4. 查看服务状态
docker-compose ps

# 5. 查看日志
docker-compose logs -f
```

### 访问服务

- **前端**: http://localhost
- **后端API**: http://localhost:8080
- **图谱计算**: http://localhost:8101
- **MySQL**: localhost:3306

### 常用命令

```bash
# 停止所有服务
docker-compose down

# 重启单个服务
docker-compose restart backend
docker-compose restart frontend
docker-compose restart kg-compute

# 重新构建服务
docker-compose up -d --build

# 查看实时日志
docker-compose logs -f backend

# 进入容器
docker-compose exec backend sh
docker-compose exec mysql mysql -uroot -p

# 清理未使用的镜像
docker system prune -f
```

## 环境变量配置

在项目根目录创建 `.env` 文件：

```bash
# MySQL配置
MYSQL_ROOT_PASSWORD=your_secure_password

# 后端配置（可选，会覆盖默认值）
SPRING_PROFILES_ACTIVE=production
JAVA_OPTS=-Xms512m -Xmx2g
KG_COMPUTE_URL=http://kg-compute:8101

# 前端配置（可选）
VITE_API_BASE_URL=http://localhost:8080
```

## 数据库初始化

如果需要初始化数据库结构，将SQL文件放入 `init-db/` 目录：

```bash
mkdir -p init-db
# 将你的初始化SQL文件放入此目录
cp your-schema.sql init-db/
```

## 数据持久化

以下数据已配置持久化存储：
- MySQL数据：`mysql_data` 卷
- 后端日志：`backend_logs` 卷
- 上传文件：`backend_uploads` 卷
- 向量数据：`backend_data` 卷

查看数据卷位置：
```bash
docker volume inspect intel-platform_mysql_data
```

## 生产环境建议

1. **修改默认密码**：务必修改 `MYSQL_ROOT_PASSWORD`
2. **配置HTTPS**：在nginx配置中添加SSL证书
3. **资源限制**：在docker-compose.yml中为每个服务添加资源限制
4. **日志轮转**：配置日志驱动和最大文件大小
5. **备份策略**：定期备份MySQL数据卷和上传文件卷

## 故障排查

```bash
# 检查服务健康状态
docker-compose ps

# 查看特定服务日志
docker-compose logs backend
docker-compose logs frontend

# 检查网络连接
docker network inspect intel-platform_intel-network

# 重启服务
docker-compose restart

# 完全重建
docker-compose down
docker-compose up -d --build
```

## 更新部署

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker-compose up -d --build

# 清理旧镜像
docker image prune -f
```
