# 技术栈

## 前端
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.x | 前端框架（Composition API + `<script setup>`） |
| TypeScript | 5.x | 类型安全 |
| Vite | 5.x | 构建工具 |
| Element Plus | 2.x | UI 组件库 |
| ECharts | 5.x | 图表与知识图谱可视化 |
| marked | latest | Markdown 渲染 |

## 后端
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.1 | 应用框架 |
| MyBatis-Plus | 3.5 | ORM 数据访问层 |
| MySQL | 8.0 | 关系数据库 |
| HikariCP | - | 数据库连接池 |

## LLM 集成
- 支持 OpenAI API 兼容接口（DeepSeek / SiliconFlow / OpenAI / Anthropic / 通义千问 等）
- 流式回答（SSE, Server-Sent Events）
- 多 Provider 可配置，独立 API Key 管理
- VLM（视觉语言模型）支持图片理解与 OCR
- 词条抽取专用 LLM（独立配置）

## 向量检索
- FAISS 向量索引
- BGE-M3 / BGE-large-zh 嵌入模型
- Rerank 重排序提升检索质量
- 兼容任意 OpenAI 格式 Embedding 接口

## 构建与部署
| 技术 | 用途 |
|------|------|
| Maven | 后端构建 |
| npm + Vite | 前端构建 |
| Tauri 2 | 桌面应用壳（可选） |
| Docker + Docker Compose | 容器化部署 |
| Rust (kg-compute) | 知识图谱计算 sidecar |

## 选型理由
- **Vue 3.5 + Element Plus**：成熟的中后台 UI 生态，低学习成本
- **Spring Boot 4.1 + MyBatis-Plus**：Java 生态最流行的微服务框架组合
- **MySQL 8.0**：稳定可靠，支持高并发读写
- **FAISS**：高性能向量相似度检索，支持 GPU 加速
- **ECharts**：功能最全的 JS 可视化库，原生支持力导向图
- **Tauri 2**：轻量桌面壳，比 Electron 更小更快
