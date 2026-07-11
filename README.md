# 智能情报分析平台

> 基于知识图谱 + RAG 的多模态智能情报分析系统

## 功能概览

- **智能问答** - 基于知识图谱的智能问答，支持图片/表格多模态回答
- **深度研究** - 多轮流式深度研究，自动生成研究报告
- **知识图谱** - 实体关系图谱可视化
- **词条百科** - 结构化知识库管理
- **图表库** - 图片和表格统一管理
- **文档管理** - PDF/Word/PPT/Excel 文档解析与知识提取

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Element Plus + Vite |
| 后端 | Spring Boot 4.1 + JDK 21 + MyBatis-Plus |
| 数据库 | MySQL 8.0 |
| 向量检索 | 自研轻量向量索引（BGE-M3 Embedding） |
| LLM | 可配置（DeepSeek / SiliconFlow / OpenAI 兼容） |
| 部署 | Docker + Docker Compose |

## 快速开始

### 方式一：Docker 一键部署（推荐）

**前置要求**：Docker 20.10+ 和 Docker Compose 2.0+

```bash
# 克隆项目
git clone https://github.com/setsu2420/Personal-KnowledgeBase.git
cd Personal-KnowledgeBase

# 一键部署
chmod +x load-and-run.sh
./load-and-run.sh
```

部署完成后访问 http://localhost

### 方式二：本地开发

**前置要求**：JDK 21+、Node.js 20+、MySQL 8.0

```bash
# 1. 创建数据库
mysql -u root -e "CREATE DATABASE intelligence_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 初始化表结构
mysql -u root intelligence_platform < init-db/01-schema.sql
mysql -u root intelligence_platform < init-db/02-init-data.sql

# 3. 启动后端
cd backend-springboot
./mvnw spring-boot:run

# 4. 启动前端（新终端）
cd frontend-vue
npm install
npm run dev
```

访问 http://localhost:5173

## 项目结构

```
├── backend-springboot/          # 后端 Spring Boot 应用
│   ├── src/main/java/
│   │   └── com/intelligence/platform/
│   │       ├── controller/      # REST API 控制器
│   │       ├── service/         # 业务逻辑层
│   │       ├── config/          # 配置类
│   │       └── entity/          # 数据实体
│   ├── pom.xml
│   └── Dockerfile
├── frontend-vue/                # 前端 Vue 3 应用
│   ├── src/
│   │   ├── views/               # 页面视图
│   │   ├── components/          # 公共组件
│   │   ├── api/                 # API 调用层
│   │   └── composables/         # 组合式函数
│   ├── Dockerfile
│   └── nginx.conf
├── init-db/                     # 数据库初始化脚本
├── docker-compose.yml           # Docker 编排配置
├── load-and-run.sh              # 一键部署脚本
└── package-delivery.sh          # 交付包构建脚本
```

## 核心功能

### 智能问答

- 基于知识图谱 + 向量检索的 RAG 问答
- 流式回答，实时显示生成过程
- 自动嵌入相关表格（Markdown 格式）
- 展示相关图片，按分数排序
- 支持会话历史管理
- 一键复制回答内容

### 深度研究

- 多轮流式研究，实时显示进度
- 自动生成结构化研究报告
- 支持引用来源追溯

### 文档处理

- 支持 PDF、Word、PPT、Excel 格式
- OCR 识别图片中的表格
- VLM 视觉语言模型辅助理解
- 自动提取知识词条

## 配置

### LLM 配置

通过管理后台 → LLM配置 页面设置：

- **对话模型**：DeepSeek-Chat / GPT-4 / 其他 OpenAI 兼容接口
- **Embedding 模型**：BGE-M3 / text-embedding-ada-002
- **视觉模型**：用于图片理解和表格 OCR

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | `intel2026secure` |
| `MYSQL_DATABASE` | 数据库名 | `intelligence_platform` |
| `JAVA_OPTS` | JVM 参数 | `-Xms512m -Xmx2g` |
| `SPRING_PROFILES_ACTIVE` | Spring 环境 | `production` |

## 交付部署

如需将项目打包交付给他人：

```bash
# 构建交付包
./package-delivery.sh

# 产出: intel-platform-delivery.tar.gz
# 接收方只需: tar -xzf intel-platform-delivery.tar.gz && cd intel-platform-delivery && ./load-and-run.sh
```

详细部署文档请参考 [DELIVERY-GUIDE.md](DELIVERY-GUIDE.md)

## 许可证

MIT License
