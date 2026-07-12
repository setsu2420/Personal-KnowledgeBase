# 智能情报分析平台

<p align="center">
  <strong>面向无人系统研究的专业化智能情报分析平台。</strong>

  上传文档、构建知识图谱、智能问答 —— 全部由大语言模型驱动。
</p>
<p align="center">
  <a href="#这是什么">这是什么？</a> •
  <a href="#功能特性">功能特性</a> •
  <a href="#系统架构">系统架构</a> •
  <a href="#技术栈">技术栈</a> •
  <a href="#下载安装">下载安装</a> •
  <a href="#从源码构建">从源码构建</a> •
  <a href="#致谢">致谢</a> •
  <a href="#许可证">许可证</a>
</p>
<p align="center">
  <a href="README.md">English</a> | 中文
</p>

---

## 功能特性

- **Graph-RAG 智能问答** — 基于知识图谱的检索增强生成，支持语义检索、多文档交叉验证、来源溯源，回答附带置信度评分
- **深度研究** — 多步推理、跨资料综合分析、过程可视化，适用于复杂课题的系统性研究
- **多格式资料管理** — 支持 PDF/Word/Excel/图片(OCR)/文本等格式上传，自动文档结构识别和关键信息提取
- **四信号知识图谱** — 直接关联、来源重叠、Adamic-Adar、关键词重叠四维相关度模型
- **Louvain 社区检测** — 自动发现知识聚类，支持内聚度评分
- **矛盾检测** — 自动识别多份资料之间的结论矛盾和数据不一致，分级分类管理
- **双空间架构** — 前台分析工作台与后台管理工作空间分离，数据共用、权限隔离
- **项目隔离** — 多项目支持，独立知识库，跨项目切换
- **本地优先** — 所有数据存储在本地 SQLite，无外部云依赖，断网可用
- **向量语义搜索** — 基于 FAISS 的嵌入检索，支持任意 OpenAI 兼容端点
- **桌面应用** — Tauri 2 原生桌面应用，系统托盘、全局快捷键、自动更新

## 这是什么？

智能情报分析平台是一个面向**无人系统（无人机/无人车/无人船/无人潜航器）研究分析人员**的专业化桌面应用。它能将你的文档自动转化为有组织、相互关联的知识库 —— 不同于传统 RAG（每次查询都从头检索回答），本系统**增量构建和维护结构化知识词条**。知识只编译一次并持续更新，而非每次查询都重新推导。

本项目基于 [Karpathy 的 LLM Wiki 模式](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f)，并受 [LLM Wiki](https://github.com/nashsu/llm_wiki) 启发。我们将核心理念实现为完整的全栈桌面应用，并在专业化文档管理与分析方面做了大量增强。

### 与 LLM Wiki 的区别

| 方面 | LLM Wiki | 智能情报分析平台 |
|------|----------|-----------------|
| 定位 | 通用个人知识库 | 面向无人系统研究的专业化情报平台 |
| 架构 | 单应用 (Tauri + React) | 双应用 (Spring Boot + Vue 3 + Tauri) |
| 数据库 | 文件系统 Wiki | SQLite + MyBatis-Plus ORM |
| 前端 | React + shadcn/ui | Vue 3 + Element Plus |
| 文档类型 | Markdown、网页 | PDF、Word、Excel、图片(OCR)、文本 |
| 知识格式 | Wiki 页面 (YAML frontmatter) | 结构化词条 + 分类体系 |
| 项目模型 | 单一 Wiki | 多项目隔离 |
| 图谱可视化 | sigma.js | ECharts |
| 特色功能 | 浏览器剪藏、Obsidian 兼容 | 矛盾检测、方向建议、研究报告生成 |

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                  前端（Vue 3 + Element Plus）             │
│  ┌──────────────┐     ┌──────────────────────────────┐  │
│  │   前台       │     │       后台                   │  │
│  │  （分析）    │     │    （管理）                  │  │
│  │  词条百科,   │     │  仪表盘, 来源, 报告          │  │
│  │  智能问答,   │     │  图表库, 图谱, 配置...       │  │
│  │  深度研究    │     │                              │  │
│  └──────┬───────┘     └──────────┬───────────────────┘  │
└─────────┼────────────────────────┼──────────────────────┘
          │    REST API（HTTP）    │
┌─────────┼────────────────────────┼──────────────────────┐
│         ▼                        ▼                      │
│              后端（Spring Boot 4.1）                    │
│  ┌─────────────┐  ┌──────────┐  ┌───────────┐  ┌────┐ │
│  │  LLM 服务   │  │  向量    │  │  文档上传  │  │图谱│ │
│  │  (对话/     │  │  搜索    │  │  服务      │  │服务│ │
│  │  嵌入)      │  │  (FAISS) │  │           │  │    │ │
│  └──────┬──────┘  └────┬─────┘  └─────┬─────┘  └──┬─┘ │
│         │              │              │             │   │
│  ┌──────▼──────────────▼──────────────▼─────────────▼┐  │
│  │           SQLite 数据库（WAL 模式）               │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
          │
          ▼
   外部 LLM API
   （DeepSeek、SiliconFlow、OpenAI 等）
```

## 功能详解

### 1. 双空间架构

应用采用前台（分析工作空间）与后台（管理工作空间）分离的设计：

**前台 — 分析工作空间：**
- **词条百科** — 知识词条卡片列表，搜索过滤，详情弹窗（Markdown 渲染），区分图片/表格/文档等词条类型
- **智能问答** — Graph-RAG 对话式问答，左侧会话历史、右侧对话区，支持引用来源面板、多轮会话
- **深度研究** — 左侧任务列表 + 新建研究，右侧结果展示，多步推理进度条、跨资料综合分析

**后台 — 管理工作空间：**
- **仪表盘** — 统计卡片（资料数/项目数）、最新资料表格、活跃项目
- **动态信息库** — 网址添加、文件上传、重新抽取
- **研究报告库** — 搜索/分页/上传/状态筛选
- **图表库** — 图片/表格分类上传、OCR 识别、多表格合并
- **项目库** — 项目列表、新建/编辑、项目详情
- **知识图谱** — ECharts 力导向布局、按类型/社区着色、构建图谱
- **来源管理** — 来源文档列表、导入文件夹、刷新来源
- **系统配置** — LLM/OCR/向量嵌入/网络搜索/图谱构建/通用设置

### 2. Graph-RAG 智能问答

不同于传统 RAG 每次查询都从头检索和回答，本系统采用**基于知识图谱的检索增强生成**：

```
阶段 1：语义检索
  - FAISS 向量索引快速查找语义相关文档
  - 支持中文 CJK 分词 + 英文停用词过滤
  - 标题匹配加分（+10 分）

阶段 2：图谱扩展
  - 四信号相关度模型发现关联页面
  - 2 跳遍历带衰减，发现更深层关联

阶段 3：上下文组装
  - 编号页面附完整内容
  - LLM 按编号引用来源：[1]、[2] 等
  - 支持置信度评分和跨文档验证
```

### 3. 四信号知识图谱

基于 ECharts 的力导向图布局，实现了完整的相关度引擎：

**四信号相关度模型：**

| 信号 | 权重 | 描述 |
|------|------|------|
| 直接关联 | ×3.0 | 直接链接的页面 |
| 来源重叠 | ×4.0 | 共享同一原始资料的页面 |
| Adamic-Adar | ×1.5 | 共享共同邻居的页面 |
| 关键词重叠 | ×1.0 | 关键词交集越大多高 |

**图谱可视化：**
- 按页面类型或社区着色节点
- 边的粗细和颜色按关联权重变化
- 缩放控件（放大、缩小、适应屏幕）
- Louvain 社区检测 + 内聚度评分
- 孤立节点/稀疏连接等图谱洞察

### 4. 深度研究

当系统识别出知识空白时：

- **多步推理** — LLM 逐步分析，显示推理进度条
- **跨资料综合** — 综合多份资料的观点，交叉验证
- **过程可视化** — 研究过程实时流式展示
- **报告生成** — 自动生成结构化研究报告
- **自动摄入** — 研究结果自动提取实体/概念到知识库

### 5. 矛盾检测与方向建议

原始设计中没有。系统**自动分析多份资料之间的结论**：

- **结论冲突检测** — 识别不同资料对同一问题的矛盾描述
- **数据不一致** — 发现数值、日期等数据差异
- **分级分类** — 按严重程度（高/中/低）分类管理
- **方向建议** — 基于已有资料生成研究方向建议

### 6. 多格式文档支持

| 格式 | 处理方式 |
|------|---------|
| PDF | Apache PDFBox 解析，支持文本提取和结构识别 |
| Word (DOCX) | Apache POI 解析，保留标题/加粗/列表/表格结构 |
| Excel (XLSX) | Apache POI 解析，多工作表支持 |
| 图片 | Tesseract OCR 文字识别 |
| 文本/Markdown | 直接读取，UTF-8 编码 |

### 7. 桌面应用特性

基于 **Tauri 2** 构建的原生桌面应用：

- **系统托盘** — 显示/隐藏窗口、重启后端、打开数据目录、退出应用
- **后端状态监控** — 三色状态指示器（绿/黄/红），点击查看 JVM 内存等详情
- **启动加载界面** — 科技感 Logo 动画 + 加载状态，30 秒超时检测
- **全局快捷键** — `Cmd/Ctrl+Q` 退出、`Cmd/Ctrl+W` 隐藏窗口、`Cmd/Ctrl+,` 打开设置
- **首次启动向导** — 配置 LLM API Key、选择数据存储位置、创建示例项目
- **自动更新** — 内置 Tauri updater，从 GitHub Releases 检查更新
- **错误处理** — 全局 ErrorBoundary，友好中文提示，自动重试（最多 3 次）
- **单实例保护** — 防止多开，二次启动时聚焦已有窗口

### 8. 项目数据隔离

- 通过 `X-Project-Id` 请求头实现多项目数据完全隔离
- 每个项目独立的知识库、资料、图表和配置
- 前台顶部栏一键切换项目
- 支持项目新建/编辑/删除

### 9. 向量语义搜索

- **FAISS 向量索引** — 基于 SiliconFlow Embedding API（BGE-large-zh-v1.5）
- **语义检索** — 即使没有关键词重叠也能发现相关文档
- **混合检索** — 向量搜索 + 关键词搜索 + 图谱扩展三路融合
- **完全可选** — 默认关闭，在设置中开启，支持任意 OpenAI 兼容端点

## 技术栈

| 层级 | 技术 |
|------|------|
| 桌面 | Tauri 2（Rust 后端 + Spring Boot Sidecar） |
| 前端 | Vue 3.5 + Element Plus 2.14 + TypeScript + Vite |
| 后端 | Spring Boot 4.1 + MyBatis-Plus 3.5 + Java 25 |
| 数据库 | SQLite（WAL 模式） |
| AI/LLM | OpenAI 兼容 API（DeepSeek、SiliconFlow、OpenAI 等） |
| 向量嵌入 | SiliconFlow Embedding API（BGE-large-zh-v1.5） |
| 向量搜索 | FAISS |
| 图谱可视化 | ECharts（力导向布局） |
| 状态管理 | Pinia |

## 下载安装

### 预编译安装包

从 [Releases](https://github.com/setsu2420/Personal-KnowledgeBase/releases) 下载：

- **macOS**：`.dmg`（Apple Silicon + Intel）

> 更多平台（Windows `.msi`、Linux `.deb`/`.AppImage`）即将推出。

### 环境要求

- **Java 21+**（后端运行依赖）
- **MySQL 8.0+**（数据库，安装后创建 `intelligence_platform` 数据库）
- LLM API 密钥（DeepSeek、OpenAI 或其他 OpenAI 兼容服务商）
- 向量嵌入 API 密钥（SiliconFlow 或其他服务商，可选）

### 快速开始

1. 下载并安装 `.dmg` 文件
2. 启动应用 → 首次启动向导将引导你完成配置
3. 进入 **后台 → 系统配置** → 配置 LLM 提供商（API 密钥 + 模型）
4. 进入 **后台 → 信息库** → 上传你的研究资料
5. 在 **前台 → 智能问答** 中开始提问

## 从源码构建

### 前置条件

- **Java 21+** 和 Maven 3.8+
- **MySQL 8.0+**（`brew install mysql && brew services start mysql`，然后 `mysql -u root -e "CREATE DATABASE intelligence_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"`）
- **Node.js 18+** 和 npm 9+
- **Rust 1.70+** 和 Cargo
- LLM API 密钥

### 1. 克隆仓库

```bash
git clone https://github.com/setsu2420/Personal-KnowledgeBase.git
cd Personal-KnowledgeBase
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env` 文件，填入你的 API 密钥：

```bash
# LLM 配置
LLM_API_KEY=sk-your-llm-api-key
LLM_API_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-chat
LLM_PROVIDER=deepseek

# 向量嵌入配置（可选）
EMBEDDING_API_KEY=sk-your-embedding-api-key
EMBEDDING_API_BASE_URL=https://api.siliconflow.cn
EMBEDDING_MODEL=BAAI/bge-large-zh-v1.5
```

### 3. 构建桌面应用

```bash
# 编译后端 JAR
cd backend-springboot && mvn clean package -DskipTests && cd ..

# 准备 Sidecar
mkdir -p src-tauri/binaries
cp backend-springboot/target/backend.jar src-tauri/binaries/

# 安装前端依赖
cd frontend-vue && npm install && cd ..

# 构建 Tauri 桌面应用
./frontend-vue/node_modules/.bin/tauri build
```

构建产物位于 `src-tauri/target/release/bundle/`：
- macOS：`dmg/*.dmg`
- Windows：`nsis/*.exe`
- Linux：`deb/*.deb`、`appimage/*.AppImage`

### 4. 开发模式

```bash
# 终端 1：启动后端
cd backend-springboot && ./mvnw spring-boot:run

# 终端 2：启动前端 + Tauri
cd frontend-vue && npm run tauri:dev
```

### 5. 仅 Web 模式（无需 Tauri）

```bash
# 终端 1：启动后端
cd backend-springboot && ./mvnw spring-boot:run

# 终端 2：启动前端
cd frontend-vue && npm run dev
```

访问 `http://localhost:5173`：
- 前台：`http://localhost:5173/portal`
- 后台：`http://localhost:5173/admin`

## Web 模式（浏览器访问）

除了 Tauri 桌面应用外，本平台还支持 **Web 模式**，完全在浏览器中运行 —— 无需安装任何桌面应用。适用于服务器部署、远程访问，或偏好浏览器操作的用户。

### 优势

- **零安装** — 无需下载安装包，启动服务即可使用
- **跨平台** — 任何现代浏览器（Chrome、Firefox、Safari、Edge）均可访问
- **多设备** — 同一局域网内的其他设备也可通过 IP 地址访问
- **功能完整** — 所有分析功能与桌面版完全一致

### 快速启动

使用提供的启动脚本一键启动：

```bash
./start-web.sh
```

脚本会自动完成以下步骤：
1. 检查 MySQL 服务，如未运行则自动启动
2. 检查数据库，如不存在则自动创建
3. 启动 Spring Boot 后端（端口 8080）
4. 等待后端就绪
5. 启动 Vue 前端（端口 5173）

### 手动启动

也可以分别启动前后端：

```bash
# 终端 1：启动后端
cd backend-springboot && ./mvnw spring-boot:run

# 终端 2：启动前端
cd frontend-vue && npm run dev
```

### 访问地址

启动后在浏览器中打开：

| 工作空间 | 地址 |
|---------|------|
| 前台（分析工作台） | http://localhost:5173/portal |
| 后台（管理工作台） | http://localhost:5173/admin |
| 后端 API | http://localhost:8080/api |
| 健康检查 | http://localhost:8080/api/health |

### 停止服务

如果通过 `./start-web.sh` 启动，按 `Ctrl+C` 即可停止所有服务。手动启动时需分别终止：

```bash
kill <后端PID>   # 停止后端
kill <前端PID>   # 停止前端
```

## 配置说明

### LLM 配置

支持两种配置方式：

1. **环境变量**（`.env` 文件）— 启动时加载默认配置
2. **应用界面** — 进入后台 → 系统配置 → 大语言模型

支持的提供商：DeepSeek、OpenAI、Anthropic、Azure OpenAI、SiliconFlow、通义千问、Moonshot（Kimi）、Google Gemini、Ollama 等。

### 数据存储

- 数据库：`{app_data_dir}/intelligence_platform.db`（SQLite WAL 模式）
- 上传文件：`{app_data_dir}/uploads/`
- 日志：`{app_data_dir}/logs/`

## 项目结构

```
Personal-KnowledgeBase/
├── frontend-vue/                 # Vue 3 前端
│   ├── src/
│   │   ├── views/portal/         # 前台页面（词条百科、智能问答、深度研究）
│   │   ├── views/admin/          # 后台页面（仪表盘、来源、报告、配置等）
│   │   ├── components/           # 公共组件（启动加载、状态监控、错误处理）
│   │   ├── composables/          # Tauri API 封装
│   │   ├── api/                  # Axios API 封装
│   │   ├── router/               # Vue Router 路由
│   │   └── store/                # Pinia 状态管理
│   └── package.json
├── backend-springboot/           # Spring Boot 后端
│   └── src/main/java/.../
│       ├── controller/           # 23 个 REST Controller
│       ├── service/              # 业务逻辑层
│       ├── mapper/               # MyBatis-Plus 数据访问
│       └── model/                # 数据模型
├── src-tauri/                    # Tauri 桌面壳
│   ├── src/
│   │   ├── lib.rs                # 应用配置、快捷键、插件注册
│   │   ├── sidecar.rs            # Spring Boot JAR 启停
│   │   └── tray.rs               # 系统托盘
│   ├── icons/                    # 应用图标
│   └── tauri.conf.json
├── scripts/                      # 构建脚本
│   ├── build-macos.sh
│   ├── build-windows.ps1
│   ├── build-linux.sh
│   └── build-all.sh
├── docs/                         # 项目文档 + 架构图
├── .env.example                  # 环境变量模板
└── README_CN.md
```

## 文档

- [系统架构](docs/architecture.md) — 系统架构与技术栈详情
- [API 参考](docs/api-reference.md) — 完整 REST API 文档（100+ 端点）
- [前端页面](docs/frontend-pages.md) — 页面描述与导航结构
- [项目介绍](docs/project-introduction.md) — 详细功能需求规格

## 致谢

核心方法论来源于 **Andrej Karpathy** 的 [LLM Wiki 模式](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f)，描述了使用 LLM 增量构建和维护个人 Wiki 的设计模式。

特别感谢 Yong Su 的 [LLM Wiki](https://github.com/nashsu/llm_wiki) 项目，其具体的桌面应用实现启发了本项目的诸多功能设计，包括知识图谱相关度模型、社区检测和深度研究工作流。

基于 [Vue 3](https://vuejs.org/)、[Element Plus](https://element-plus.org/)、[Spring Boot](https://spring.io/projects/spring-boot)、[Tauri 2](https://tauri.app/) 和 [ECharts](https://echarts.apache.org/) 构建。

## 许可证

本项目基于 MIT 许可证开源 — 详见 [LICENSE](LICENSE) 文件。
