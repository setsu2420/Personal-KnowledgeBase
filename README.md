<p align="center">
  <h1 align="center">IntelliSense Platform</h1>
  <p align="center">
    <strong>Intelligence Analysis Platform for Unmanned Systems Research</strong><br>
    Upload documents → Build knowledge graphs → Ask intelligent questions — all powered by LLMs.
  </p>
</p>

<p align="center">
  <a href="https://github.com/setsu2420/Personal-KnowledgeBase/stargazers"><img src="https://img.shields.io/github/stars/setsu2420/Personal-KnowledgeBase?style=flat-square&color=yellow" alt="Stars"></a>
  <a href="https://github.com/setsu2420/Personal-KnowledgeBase/blob/main/LICENSE"><img src="https://img.shields.io/github/license/setsu2420/Personal-KnowledgeBase?style=flat-square&color=blue" alt="License"></a>
  <a href="https://github.com/setsu2420/Personal-KnowledgeBase/releases"><img src="https://img.shields.io/github/v/release/setsu2420/Personal-KnowledgeBase?style=flat-square&color=green" alt="Release"></a>
  <br>
  <img src="https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk" alt="Java">
  <img src="https://img.shields.io/badge/Vue-3.5-brightgreen?style=flat-square&logo=vuedotjs" alt="Vue">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=flat-square&logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Tauri-2.0-FFC131?style=flat-square&logo=tauri" alt="Tauri">
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql" alt="MySQL">
</p>

<p align="center">
  English | <a href="README_CN.md">中文</a>
</p>

---

## 📖 Table of Contents

- [✨ Interface Showcase](#-interface-showcase)
- [🤔 What is IntelliSense?](#-what-is-intellisense)
- [🚀 Core Features](#-core-features)
- [🏗️ Architecture](#️-architecture)
- [🛠️ Tech Stack](#️-tech-stack)
- [⚡ Quick Start](#-quick-start)
- [🔍 Feature Deep Dive](#-feature-deep-dive)
  - [1. Graph-RAG Intelligent Q&A](#1-graph-rag-intelligent-qa)
  - [2. Four-Signal Knowledge Graph](#2-four-signal-knowledge-graph)
  - [3. Deep Research](#3-deep-research)
  - [4. Contradiction Detection](#4-contradiction-detection)
  - [5. Multi-Format Document Support](#5-multi-format-document-support)
  - [6. Desktop Application (Tauri 2)](#6-desktop-application-tauri-2)
- [⚖️ Comparison with LLM Wiki](#️-comparison-with-llm-wiki)
- [📁 Project Structure](#-project-structure)
- [⚙️ Configuration](#️-configuration)
- [📚 Documentation](#-documentation)
- [🙏 Credits](#-credits)
- [📄 License](#-license)

---

## ✨ Interface Showcase

<table align="center">
  <tr>
    <td align="center"><strong>🤖 Smart Q&A</strong></td>
    <td align="center"><strong>🔗 Knowledge Graph</strong></td>
  </tr>
  <tr>
    <td><img src="pics/QA.png" alt="Smart Q&A Interface" /></td>
    <td><img src="pics/KG.png" alt="Knowledge Graph Visualization" /></td>
  </tr>
  <tr>
    <td align="center"><strong>📚 Wiki Entries</strong></td>
    <td align="center"><strong>🖼️ Image Management</strong></td>
  </tr>
  <tr>
    <td><img src="pics/entry.png" alt="Wiki Entry Browser" /></td>
    <td><img src="pics/pictures.png" alt="Image Management Dashboard" /></td>
  </tr>
</table>

---

## 🤔 What is IntelliSense?

**IntelliSense Platform** is a specialized desktop application built for **unmanned systems (UAV / UGV / USV / UUV) researchers and analysts**. It transforms your documents — PDFs, Word files, spreadsheets, images — into an organized, interlinked, and queryable knowledge base.

> Unlike traditional RAG systems that retrieve and re-derive answers from scratch on every query, IntelliSense **incrementally builds and maintains structured knowledge entries**. Knowledge is compiled once, kept current, and referenced directly — not re-derived each time.

### 🧬 Design Philosophy

- **Knowledge-first, not retrieval-first** — Structured entries > ephemeral retrieval
- **Local & offline** — Your data never leaves your machine
- **Multi-project isolation** — One tool, many projects, zero data leakage
- **LLM-augmented, human-curated** — AI does the heavy lifting; you stay in control

This project builds upon [Andrej Karpathy's LLM Wiki pattern](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f) and draws inspiration from [LLM Wiki](https://github.com/nashsu/llm_wiki), extended significantly for specialized document analysis in the unmanned systems domain.

---

## 🚀 Core Features

| # | Feature | Description |
|---|---------|-------------|
| 🔍 | **Graph-RAG Q&A** | Knowledge-graph-based retrieval-augmented generation with semantic search, cross-document verification, source traceability, and confidence scoring |
| 🧠 | **Deep Research** | Multi-step LLM reasoning with cross-source synthesis and real-time process visualization |
| 📄 | **Multi-Format Parsing** | PDF, Word, Excel, Images (OCR), and plain text — automatic structure recognition and key information extraction |
| 🔗 | **4-Signal Knowledge Graph** | Direct links, source overlap, Adamic-Adar, and keyword overlap — a four-dimensional relevance model |
| 🧩 | **Louvain Community Detection** | Automatic knowledge cluster discovery with cohesion scoring |
| ⚠️ | **Contradiction Detection** | Auto-identify conflicting conclusions and data inconsistencies across sources, graded by severity |
| 🏠 | **Dual-Space Architecture** | Front-end analysis workspace + back-end management workspace — shared data, isolated permissions |
| 📦 | **Project Isolation** | Multi-project support with independent knowledge bases and one-click switching |
| 🖥️ | **Local-First** | All data stored locally — no cloud dependencies, works fully offline |
| 🧲 | **Vector Semantic Search** | FAISS-powered embedding retrieval — compatible with any OpenAI-format endpoint |
| 🪟 | **Native Desktop App** | Tauri 2 desktop application with system tray, global shortcuts, and auto-update |

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                   Frontend (Vue 3 + Element Plus)                │
│  ┌──────────────────────┐     ┌──────────────────────────────┐  │
│  │   Analysis Workspace │     │    Management Workspace      │  │
│  │                      │     │                              │  │
│  │  • Wiki Browser      │     │  • Dashboard & Analytics     │  │
│  │  • Smart Q&A         │     │  • Source Management         │  │
│  │  • Deep Research     │     │  • Graph Visualization       │  │
│  │  • Knowledge Graph   │     │  • System Configuration      │  │
│  └──────────┬───────────┘     └──────────────┬───────────────┘  │
└─────────────┼────────────────────────────────┼──────────────────┘
              │        REST API (HTTP)         │
┌─────────────┼────────────────────────────────┼──────────────────┐
│             ▼                                ▼                  │
│                 Backend (Spring Boot 4.1)                       │
│  ┌───────────────┐ ┌──────────────┐ ┌───────────┐ ┌─────────┐ │
│  │  LLM Service  │ │ Vector Store │ │ Document  │ │  Graph  │ │
│  │  (Chat/Embed) │ │   (FAISS)    │ │  Parser   │ │ Engine  │ │
│  └───────┬───────┘ └──────┬───────┘ └─────┬─────┘ └────┬────┘ │
│          │                │               │            │       │
│  ┌───────┴────────────────┴───────────────┴────────────┴─────┐ │
│  │                   MySQL 8.0 Database                      │ │
│  └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
          │
          ▼
   External LLM APIs
   (DeepSeek • SiliconFlow • OpenAI • Anthropic • …)
```

**Data Flow**: Documents → Parsed & Chunked → Vectorized & Indexed → Knowledge Graph constructed → Graph-RAG ready for Q&A

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| 🖥️ Desktop Shell | **Tauri 2** (Rust) | Native windowing, system tray, sidecar management |
| 🎨 Frontend | **Vue 3.5** + **Element Plus 2.14** + **TypeScript** | Reactive UI framework with enterprise-grade components |
| ⚙️ Build Tool | **Vite** | Blazing-fast dev server and bundler |
| ☕ Backend | **Spring Boot 4.1** + **MyBatis-Plus 3.5** | Production-grade Java backend |
| 🗄️ Database | **MySQL 8.0** | Reliable, performant RDBMS |
| 🤖 AI / LLM | OpenAI-compatible APIs | Multi-provider support (DeepSeek, OpenAI, Anthropic, etc.) |
| 🧲 Embedding | BGE-M3 / BGE-large-zh | High-quality Chinese-English bilingual embeddings |
| 📊 Vector Search | **FAISS** | GPU-accelerated approximate nearest neighbor |
| 🔗 Graph Viz | **ECharts** | Interactive force-directed knowledge graphs |
| 🗃️ State Mgmt | **Pinia** | Lightweight, type-safe Vue state management |
| 🐳 Deployment | **Docker** + **Docker Compose** | One-command production deployment |

---

## ⚡ Quick Start

### 🐳 Option 1: Docker (Recommended)

> **Prerequisites**: Docker 20.10+ & Docker Compose 2.0+

```bash
git clone https://github.com/setsu2420/Personal-KnowledgeBase.git
cd Personal-KnowledgeBase
chmod +x load-and-run.sh
./load-and-run.sh
```

Open [http://localhost](http://localhost) and start exploring.

### 💻 Option 2: Local Development

> **Prerequisites**: JDK 21+, Node.js 20+, MySQL 8.0

```bash
# 1. Create the database
mysql -u root -e "CREATE DATABASE intelligence_platform \
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. Initialize schema and seed data
mysql -u root intelligence_platform < init-db/01-schema.sql
mysql -u root intelligence_platform < init-db/02-init-data.sql

# 3. Start the backend
cd backend-springboot
./mvnw spring-boot:run

# 4. Start the frontend (in a new terminal)
cd frontend-vue
npm install
npm run dev
```

Visit [http://localhost:5173](http://localhost:5173)

| Workspace | URL |
|-----------|-----|
| 🧑‍🔬 Analysis (Portal) | `http://localhost:5173/portal` |
| ⚙️ Management (Admin) | `http://localhost:5173/admin` |
| 🔌 Backend API | `http://localhost:8080/api` |

---

## 🔍 Feature Deep Dive

### 1. Graph-RAG Intelligent Q&A

Unlike naive RAG, our **graph-aware retrieval pipeline** ensures richer context and higher answer quality:

```
Phase 1 — Semantic Retrieval
  ├─ FAISS ANN search with CJK-aware tokenization
  ├─ English stop-word filtering
  └─ Title match bonus (+10 score boost)

Phase 2 — Graph Expansion
  ├─ 4-signal relevance scoring across the knowledge graph
  └─ 2-hop traversal with decay factor

Phase 3 — Context Assembly
  ├─ Numbered passages fed to the LLM
  ├─ Inline source citations: [1], [2], …
  ├─ Confidence score per answer
  └─ Cross-document verification flag
```

### 2. Four-Signal Knowledge Graph

Visualized as an interactive ECharts force-directed graph:

| Signal | Weight | What it captures |
|--------|:------:|------------------|
| 🔗 **Direct Link** | ×3.0 | Pages explicitly linked together |
| 📎 **Source Overlap** | ×4.0 | Pages derived from the same source document |
| 🧮 **Adamic-Adar** | ×1.5 | Pages with many shared neighbors (community signal) |
| 🔑 **Keyword Overlap** | ×1.0 | Shared topical keywords |

**Graph Capabilities**:
- 🎨 Color by type or community (toggleable)
- 🔍 Zoom, pan, fit-to-view
- 🧩 Louvain community detection + cohesion scoring
- 💡 Graph insights: bridge nodes, isolated pages, density metrics

### 3. Deep Research

Triggered when the system detects knowledge gaps beyond existing coverage:

- **Multi-step reasoning** — LLM breaks down complex questions, shown as a progress pipeline
- **Cross-source synthesis** — Corroborates findings across multiple documents
- **Live process visualization** — Streaming updates as the research unfolds
- **Auto-generated report** — Structured markdown research report with citations
- **Automatic ingestion** — Extracted entities & concepts are merged back into the knowledge base

### 4. Contradiction Detection

- 🔴 **High**: Directly conflicting conclusions on the same claim
- 🟡 **Medium**: Data inconsistencies (numbers, dates, figures)
- 🟢 **Low**: Divergent interpretations or nuance differences
- 🧭 Direction suggestions based on detected knowledge gaps

### 5. Multi-Format Document Support

| Format | Engine | Capabilities |
|--------|--------|-------------|
| 📕 PDF | Apache PDFBox | Text extraction, structure recognition |
| 📘 Word (DOCX) | Apache POI | Preserves headings, bold, lists, tables |
| 📗 Excel (XLSX) | Apache POI | Multi-sheet parsing |
| 🖼️ Images | Tesseract OCR + VLM | Text recognition + visual understanding |
| 📝 Text / Markdown | Native reader | UTF-8, YAML frontmatter support |

### 6. Desktop Application (Tauri 2)

| Feature | Description |
|---------|-------------|
| 🖥️ **System Tray** | Show/hide, restart backend, open data dir, quit |
| 🟢 **Backend Monitor** | Green / Yellow / Red status indicator + JVM memory details |
| ✨ **Splash Screen** | Animated logo with loading progress; 30s timeout fallback |
| ⌨️ **Global Shortcuts** | `Cmd/Ctrl+Q` quit, `Cmd/Ctrl+W` hide, `Cmd/Ctrl+,` settings |
| 🧙 **First-Run Wizard** | API key config, data directory selection, sample project |
| 🔄 **Auto-Update** | Tauri updater from GitHub Releases |
| 🛡️ **Error Handling** | Global ErrorBoundary, friendly messages, auto-retry (×3) |
| 🔒 **Single Instance** | Prevents duplicate launches; focuses existing window |

---

## ⚖️ Comparison with LLM Wiki

| Dimension | LLM Wiki | IntelliSense Platform |
|-----------|----------|-----------------------|
| 🎯 **Positioning** | General personal wiki | Domain-specific intelligence platform (unmanned systems) |
| 🏛️ **Architecture** | Monolithic (Tauri + React) | Dual-app (Spring Boot + Vue 3 + Tauri) |
| 🗄️ **Storage** | File-system wiki | MySQL + MyBatis-Plus ORM |
| 🎨 **Frontend** | React + shadcn/ui | Vue 3 + Element Plus |
| 📄 **Input Formats** | Markdown, web pages | PDF, Word, Excel, Images (OCR), Text |
| 📦 **Knowledge Model** | Wiki pages (YAML frontmatter) | Structured entries + classification taxonomy |
| 📁 **Project Model** | Single wiki | Multi-project isolation |
| 📊 **Graph Viz** | sigma.js | ECharts (force-directed + interactive) |
| ⭐ **Unique Strengths** | Web clipper, Obsidian compat | Contradiction detection, deep research, research direction suggestions |

---

## 📁 Project Structure

```
Personal-KnowledgeBase/
├── backend-springboot/          # ☕ Spring Boot backend
│   ├── src/main/java/com/intelligence/platform/
│   │   ├── controller/          #   REST API controllers (23 endpoints)
│   │   ├── service/             #   Business logic layer
│   │   ├── config/              #   Spring configuration
│   │   ├── entity/              #   JPA / MyBatis-Plus entities
│   │   └── mapper/              #   Data access mappers
│   ├── pom.xml
│   └── Dockerfile
├── frontend-vue/                # 🎨 Vue 3 frontend
│   ├── src/
│   │   ├── views/               #   Page views (portal & admin)
│   │   ├── components/          #   Reusable UI components
│   │   ├── composables/         #   Tauri API wrappers
│   │   ├── api/                 #   Axios API layer
│   │   ├── router/              #   Vue Router config
│   │   └── store/               #   Pinia state stores
│   ├── Dockerfile
│   └── nginx.conf
├── src-tauri/                   # 🦀 Tauri 2 desktop shell
│   ├── src/                     #   Rust backend (sidecar, tray, shortcuts)
│   ├── icons/                   #   App icons
│   └── tauri.conf.json
├── init-db/                     # 🗄️ Database init scripts
├── scripts/                     # 🔧 Build & utility scripts
├── config/                      # ⚙️ Application configuration
├── docs/                        # 📚 Documentation
├── docker-compose.yml           # 🐳 Docker orchestration
├── load-and-run.sh              # 🚀 One-click deploy
└── package-delivery.sh          # 📦 Delivery build script
```

---

## ⚙️ Configuration

### 🤖 LLM Providers

Navigate to **Admin → System Settings → LLM Configuration** to set up:

- **Chat**: DeepSeek-Chat, GPT-4o, Claude, Gemini — any OpenAI-compatible endpoint
- **Embedding**: BGE-M3, text-embedding-3, BGE-large-zh
- **Vision**: For image understanding and table OCR

### 🌍 Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MYSQL_ROOT_PASSWORD` | MySQL root password | `intel2026secure` |
| `MYSQL_DATABASE` | Database name | `intelligence_platform` |
| `JAVA_OPTS` | JVM arguments | `-Xms512m -Xmx2g` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `production` |

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [📐 Architecture](docs/architecture.md) | System architecture, design decisions, and data flow |
| [📡 API Reference](docs/api-reference.md) | Full REST API documentation (100+ endpoints) |
| [📋 Changelog](docs/changelog.md) | Release notes and version history |
| [🛠️ Tech Stack](docs/TECH_STACK.md) | Detailed technology choices and rationale |
| [📖 Usage Guide](docs/usage.md) | End-user manual and workflow guide |

---

## 🙏 Credits

- **Andrej Karpathy** — for the [LLM Wiki pattern](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f) that started it all
- **Yong Su (nashsu)** — for the [LLM Wiki](https://github.com/nashsu/llm_wiki) implementation that inspired much of our design
- Built with ❤️ using [Vue 3](https://vuejs.org/), [Element Plus](https://element-plus.org/), [Spring Boot](https://spring.io/projects/spring-boot), [Tauri 2](https://tauri.app/), and [ECharts](https://echarts.apache.org/)

---

## 📄 License

This project is open source under the [MIT License](LICENSE).

---

<p align="center">
  <sub>Made with ❤️ for unmanned systems researchers everywhere</sub>
</p>
