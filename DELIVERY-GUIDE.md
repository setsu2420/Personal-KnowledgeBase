# 智能情报分析平台 - 完整交付包

## 📦 包内容说明

本交付包包含智能情报分析平台的完整源代码和部署配置，接收方可在具备Docker环境的机器上一键部署。

### 目录结构
```
intel-platform-source/
├── backend-springboot/          # 后端Spring Boot应用
│   ├── src/                     # Java源代码
│   ├── pom.xml                  # Maven配置
│   ├── Dockerfile              # 后端Docker构建文件
│   └── mvnw*                   # Maven包装器
├── frontend-vue/               # 前端Vue应用
│   ├── src/                     # Vue源代码
│   ├── package.json            # NPM配置
│   ├── Dockerfile              # 前端Docker构建文件
│   └── nginx.conf              # Nginx配置
├── init-db/                    # 数据库初始化脚本
│   ├── 00-create-db.sql        # 创建数据库
│   ├── 01-schema.sql           # 表结构
│   └── 02-init-data.sql        # 初始数据
├── docker-compose.yml          # Docker编排配置
├── .env.example                # 环境变量模板
├── .dockerignore               # Docker忽略文件
├── build-delivery.sh           # 构建交付包脚本（可选）
├── load-and-run.sh             # 一键部署脚本
└── README.md                   # 本说明文档
```

## 🚀 快速部署（推荐）

### 前置要求
- Docker 20.10+
- Docker Compose 2.0+
- 至少 4GB 可用内存
- 至少 10GB 可用磁盘空间

### 一键部署
```bash
# 1. 进入交付目录
cd intel-platform-source

# 2. 执行一键部署脚本
chmod +x load-and-run.sh
./load-and-run.sh
```

部署完成后访问：
- **前端界面**: http://localhost
- **后端API**: http://localhost:8080

## 📋 手动部署步骤

如果需要手动控制部署过程：

```bash
# 1. 复制环境变量配置
cp .env.example .env

# 2. （可选）编辑配置
# 修改 .env 中的密码等配置
nano .env

# 3. 构建并启动服务
docker-compose up -d --build

# 4. 查看服务状态
docker-compose ps

# 5. 查看日志
docker-compose logs -f
```

## 🔧 常用运维命令

### 服务管理
```bash
# 查看所有服务状态
docker-compose ps

# 启动所有服务
docker-compose up -d

# 停止所有服务
docker-compose down

# 重启所有服务
docker-compose restart

# 重启单个服务
docker-compose restart backend
docker-compose restart frontend
```

### 日志查看
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看后端日志
docker-compose logs -f backend

# 查看前端日志
docker-compose logs -f frontend

# 查看最近100行日志
docker-compose logs --tail=100 backend
```

### 数据库操作
```bash
# 进入MySQL容器
docker-compose exec mysql mysql -uroot -p

# 备份数据库
docker-compose exec mysql mysqldump -uroot -p intelligence_platform > backup.sql

# 恢复数据库
docker-compose exec -T mysql mysql -uroot -p intelligence_platform < backup.sql
```

### 更新部署
```bash
# 停止服务
docker-compose down

# 重新构建并启动
docker-compose up -d --build

# 清理未使用的镜像
docker image prune -f
```

## 🗄️ 数据持久化

以下数据已配置持久化存储（Docker卷）：

| 数据卷 | 用途 | 位置 |
|--------|------|------|
| mysql_data | MySQL数据库 | /var/lib/mysql |
| backend_logs | 后端日志 | /app/logs |
| backend_uploads | 上传文件 | /app/uploads |
| backend_data | 向量索引等 | /app/data |

**注意**：执行 `docker-compose down` 不会删除数据卷，数据会保留。如需彻底清除：
```bash
docker-compose down -v  # 删除数据卷（谨慎使用）
```

## 🔐 安全配置

### 修改默认密码
编辑 `.env` 文件：
```bash
MYSQL_ROOT_PASSWORD=your_secure_password
```

### 配置HTTPS（生产环境）
1. 获取SSL证书
2. 修改 `frontend-vue/nginx.conf` 添加SSL配置
3. 重新构建前端镜像

## 🐛 故障排查

### 服务启动失败
```bash
# 查看详细错误日志
docker-compose logs backend
docker-compose logs frontend

# 检查端口占用
lsof -i :80
lsof -i :8080

# 检查MySQL连接
docker-compose exec mysql mysqladmin ping -h localhost
```

### 数据库连接问题
```bash
# 检查MySQL状态
docker-compose ps mysql

# 查看MySQL日志
docker-compose logs mysql

# 测试连接
docker-compose exec mysql mysql -uroot -p -e "SHOW DATABASES;"
```

### 前端无法访问后端API
```bash
# 检查网络连通性
docker-compose exec frontend ping backend

# 检查后端健康状态
curl http://localhost:8080/actuator/health
```

## 📊 系统要求

### 最低配置
- CPU: 2核
- 内存: 4GB
- 磁盘: 10GB
- 网络: 可访问外网（用于LLM API调用）

### 推荐配置
- CPU: 4核+
- 内存: 8GB+
- 磁盘: 50GB+
- 网络: 稳定外网连接

## 🔄 备份与恢复

### 完整备份
```bash
# 备份数据库
docker-compose exec mysql mysqldump -uroot -p intelligence_platform > backup_$(date +%Y%m%d).sql

# 备份上传文件
docker cp $(docker-compose ps -q backend):/app/uploads ./uploads_backup

# 备份配置
cp .env .env.backup
```

### 恢复数据
```bash
# 恢复数据库
docker-compose exec -T mysql mysql -uroot -p intelligence_platform < backup.sql

# 恢复上传文件
docker cp ./uploads_backup $(docker-compose ps -q backend):/app/uploads
```

## 📞 技术支持

如遇问题，请提供以下信息：
1. 操作系统版本
2. Docker版本: `docker --version`
3. Docker Compose版本: `docker-compose version`
4. 错误日志: `docker-compose logs`

## 📝 版本信息

- **平台版本**: v1.0.0
- **构建日期**: 2026-07-11
- **技术栈**: 
  - 后端: Spring Boot 4.1.0 + JDK 21
  - 前端: Vue 3 + TypeScript + Element Plus
  - 数据库: MySQL 8.0
  - 部署: Docker + Docker Compose
