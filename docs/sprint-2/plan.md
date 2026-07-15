# Phase 2 Enhancement Plan: LLM Wiki-Inspired Optimization

## Overview
参考 llm_wiki 进行功能优化、UI优化、性能优化，同时解决潜在问题、完善功能。

---

## Task 1: 潜在问题排查与修复 🔍

### 1.1 数据库隔离完整性验证
- [x] 检查所有表是否包含 `project_id` 列
- [x] 检查所有 DAO/Mapper 查询是否正确过滤 `project_id`
- [x] 检查 ProjectContext 是否在所有入口点生效
- [x] 验证 X-Project-Id 头传递完整性

### 1.2 安全性审查
- [x] 检查所有 API 端点是否有适当的访问控制 (修复12处project_id隔离缺陷)
- [x] 增强 ProjectContext.validateProjectAccess() 方法
- [x] 修复 stats 端点查询全库数据问题
- [x] 修复 DecisionController/AnalysisController/RiskController 缺少project过滤

### 1.3 性能瓶颈排查
- [x] 检查 HikariCP 连接池配置是否合理
- [x] 检查向量索引加载方式
- [x] 检查 N+1 查询问题
- [x] 检查大文件上传内存使用

---

## Task 2: URL上传与网页解析 🕷️

### 2.1 后端：网页抓取服务
- [x] 创建 `WebScrapeService.java` - 使用 Jsoup/HtmlUnit 抓取网页
- [x] 实现 Readability 算法提取正文
- [x] 实现元数据提取（标题、作者、日期、描述）
- [x] 缓存机制（SHA256，避免重复抓取）

### 2.2 后端：URL上传端点
- [x] 在 UploadController 添加 `POST /api/upload/from-url` 端点增强
- [x] 支持 WebScrapeService 抓取 + Markdown 转换
- [x] 支持 TwoStepIngestService 两步CoT摄入
- [x] 元数据保存至 Document.metaInfo

### 2.3 后端：词条抽取增强
- [x] 创建 `TwoStepIngestService.java` - Two-step CoT ingest
- [x] Step 1: LLM 分析文档结构，识别关键实体
- [x] Step 2: LLM 基于分析结果生成结构化词条
- [x] SHA256 缓存（避免重复处理）

### 2.4 前端：URL上传UI
- [x] 在 InfoDynamic.vue 添加 TwoStep 开关
- [x] 支持粘贴URL
- [x] 显示抓取元数据（作者、日期、正文长度、入库模式）
- [ ] 支持批量 URL 上传
- [ ] 显示抓取/解析进度

### 2.5 测试
- [x] 使用 Harness Engineering for Self-Improvement 文章测试
- [x] URL: https://lilianweng.github.io/posts/2026-07-04-harness/
- [x] 验证词条抽取质量
- [x] 验证加入 OPD 项目流程

---

## Task 3: 功能优化（参考 llm_wiki）⚡

### 3.1 双步 CoT 文档摄入（Two-step Chain-of-Thought Ingest）
- [x] 文档上传后，LLM 先分析文档结构
- [x] 第二步基于分析生成高质量词条
- [x] 增加 SHA256 缓存避免重复处理
- [x] 支持增量更新（findExisting dedup logic）

### 3.2 思考链/推理过程展示
- [x] SSE 流中增加 thinking 事件类型
- [x] 前端 SmartQA 增加可折叠的思考过程区域
- [x] 显示 LLM 的推理步骤
- [x] 修复跨chunk标签检测（pendingBuf缓冲机制）

### 3.3 Mermaid 图表渲染
- [x] 集成 Mermaid.js 到前端
- [x] Markdown 渲染支持 mermaid 代码块
- [x] LLM prompt 中引导生成 Mermaid 图表

### 3.4 KaTeX 数学公式渲染
- [x] 集成 KaTeX 到前端
- [x] Markdown 渲染支持数学公式
- [x] 行内公式和块级公式支持

### 3.5 多轮对话增强
- [x] 改进上下文窗口管理（历史加载+截断）
- [x] 支持对话历史传递（streamChatWithHistory）
- [ ] 支持 @提及文件/词条
- [ ] 支持 Skill/工具调用

---

## Task 4: UI 界面优化 🎨

### 4.1 整体设计系统对齐
- [x] 统一 CSS 变量（颜色、间距、字体、圆角）
- [x] 改进暗色模式支持（@media prefers-color-scheme: dark）
- [x] 统一组件风格（Element Plus 全局覆盖）

### 4.2 SmartQA 对话界面优化
- [x] 改进消息气泡样式
- [x] 添加打字机动画效果（blink-cursor）
- [x] 改进代码块渲染（语法高亮）
- [x] 优化移动端体验（reduced-motion支持）

### 4.3 项目切换优化
- [x] 改进顶部项目选择器 UI（搜索过滤 + 统计信息）
- [x] 添加项目搜索功能
- [x] 显示项目统计信息（placeholder）

### 4.4 文件管理界面优化
- [ ] 改进文档列表布局
- [ ] 添加缩略图预览
- [ ] 改进上传进度显示

---

## Task 5: 性能优化 🚀

### 5.1 前端性能
- [x] 组件懒加载优化（defineAsyncComponent路由级）
- [ ] 虚拟滚动
- [x] 图片懒加载（loading="lazy"）
- [x] 代码分割优化（Vite manualChunks）

### 5.2 后端性能
- [x] 向量索引预加载优化
- [x] 批量操作 API
- [x] 数据库索引优化（16个复合索引）
- [x] 响应压缩（Gzip + server.compression）

### 5.3 构建优化
- [x] Tree shaking 优化
- [x] 构建缓存
- [x] 依赖优化（Vite manualChunks: element-plus, echarts, mermaid, katex, marked）

---

## Execution Order

1. **Task 1** (问题排查) → 先确保基础稳固
2. **Task 3.1 + Task 2** (CoT 摄入 + URL上传) → 核心新功能
3. **Task 3.2-3.5** (功能增强) → 体验提升
4. **Task 4** (UI 优化) → 视觉打磨
5. **Task 5** (性能优化) → 收尾优化
