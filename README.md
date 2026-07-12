# Intelligent Intelligence Analysis Platform

<p align="center">
  <strong>A specialized intelligent intelligence analysis platform for unmanned systems research.</strong><br>
  Upload documents, build knowledge graphs, intelligent Q&A — all powered by LLMs.
</p>

<p align="center">
  <a href="#what-is-this">What is this?</a> •
  <a href="#features">Features</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#tech-stack">Tech Stack</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#credits">Credits</a> •
  <a href="#license">License</a>
</p>

<p align="center">
  English | <a href="README_CN.md">中文</a>
</p>

---

## Features

- **Graph-RAG Intelligent Q&A** — Knowledge graph-based retrieval-augmented generation with semantic search, cross-document verification, source traceability, and confidence scoring
- **Deep Research** — Multi-step reasoning with cross-source synthesis, process visualization, for systematic research on complex topics
- **Multi-format Document Management** — Support PDF/Word/Excel/Image(OCR)/Text uploads with automatic structure recognition and key information extraction
- **4-Signal Knowledge Graph** — Direct links, source overlap, Adamic-Adar, and keyword overlap — four-dimensional relevance model
- **Louvain Community Detection** — Automatic knowledge cluster discovery with cohesion scoring
- **Contradiction Detection** — Auto-identify contradictory conclusions and data inconsistencies across sources, graded by severity
- **Dual-Space Architecture** — Front-end analysis workspace and back-end management workspace separated, shared data, isolated permissions
- **Project Isolation** — Multi-project support with independent knowledge bases and cross-project switching
- **Local-First** — All data stored in local database, no external cloud dependencies, works offline
- **Vector Semantic Search** — FAISS-based embedding retrieval, supports any OpenAI-compatible endpoint
- **Desktop Application** — Tauri 2 native desktop app with system tray, global shortcuts, auto-update

## What is this?

The Intelligent Intelligence Analysis Platform is a specialized desktop application for **unmanned systems (UAV/UGV/USV/UUV) researchers and analysts**. It automatically transforms your documents into an organized, interlinked knowledge base — unlike traditional RAG (retrieve-and-answer from scratch every time), this system **incrementally builds and maintains structured knowledge entries**. Knowledge is compiled once and kept current, not re-derived on every query.

This project is based on [Karpathy's LLM Wiki pattern](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f) and inspired by [LLM Wiki](https://github.com/nashsu/llm_wiki). We implemented the core ideas as a full-stack desktop application with significant enhancements in specialized document management and analysis.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Frontend (Vue 3 + Element Plus)         │
│  ┌──────────────┐     ┌──────────────────────────────┐  │
│  │   Frontend   │     │         Backend              │  │
│  │  (Analysis)  │     │      (Management)            │  │
│  │  Wiki, Q&A,  │     │  Dashboard, Sources,         │  │
│  │  Deep Research│     │  Charts, Graph, Config...    │  │
│  └──────┬───────┘     └──────────┬───────────────────┘  │
└─────────┼────────────────────────┼──────────────────────┘
          │    REST API (HTTP)     │
┌─────────┼────────────────────────┼──────────────────────┐
│         ▼                        ▼                      │
│              Backend (Spring Boot 4.1)                  │
│  ┌─────────────┐  ┌──────────┐  ┌───────────┐  ┌────┐ │
│  │  LLM Service│  │  Vector  │  │  Document │  │Graph│ │
│  │  (Chat/     │  │  Search  │  │  Upload   │  │Svc  │ │
│  │   Embed)    │  │  (FAISS) │  │  Service  │  │     │ │
│  └──────┬──────┘  └────┬─────┘  └─────┬─────┘  └──┬─┘ │
│         │              │              │             │   │
│  ┌──────▼──────────────▼──────────────▼─────────────▼┐  │
│  │           MySQL 8.0 Database                      │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
          │
          ▼
   External LLM APIs
   (DeepSeek, SiliconFlow, OpenAI, etc.)
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Desktop | Tauri 2 (Rust backend + Spring Boot sidecar) |
| Frontend | Vue 3.5 + Element Plus 2.14 + TypeScript + Vite |
| Backend | Spring Boot 4.1 + MyBatis-Plus 3.5 + Java 25 |
| Database | MySQL 8.0 |
| AI / LLM | OpenAI-compatible API (DeepSeek, SiliconFlow, OpenAI, etc.) |
| Vector Embedding | SiliconFlow Embedding API (BGE-M3) |
| Vector Search | Custom lightweight vector index (FAISS-compatible) |
| Graph Visualization | ECharts (force-directed layout) |
| State Management | Pinia |
| Deployment | Docker + Docker Compose |

## Quick Start

### Option 1: Docker (Recommended)

**Prerequisites**: Docker 20.10+ and Docker Compose 2.0+

```bash
git clone https://github.com/setsu2420/Personal-KnowledgeBase.git
cd Personal-KnowledgeBase
chmod +x load-and-run.sh
./load-and-run.sh
```

Visit http://localhost after deployment.

### Option 2: Local Development

**Prerequisites**: JDK 21+, Node.js 20+, MySQL 8.0

```bash
# 1. Create database
mysql -u root -e "CREATE DATABASE intelligence_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. Initialize schema
mysql -u root intelligence_platform < init-db/01-schema.sql
mysql -u root intelligence_platform < init-db/02-init-data.sql

# 3. Start backend
cd backend-springboot
./mvnw spring-boot:run

# 4. Start frontend (new terminal)
cd frontend-vue
npm install
npm run dev
```

Visit http://localhost:5173

## Core Features In-Depth

### 1. Graph-RAG Intelligent Q&A

Unlike traditional RAG that retrieves from scratch for every query, this system uses knowledge graph-based retrieval:

```
Phase 1: Semantic Retrieval
  - FAISS vector index for fast ANN search
  - Chinese CJK tokenization + English stop word filtering
  - Title match bonus (+10 score)

Phase 2: Graph Expansion
  - 4-signal relevance model for related pages
  - 2-hop traversal with decay

Phase 3: Context Assembly
  - Numbered pages with full content
  - LLM cites sources by number: [1], [2], etc.
  - Confidence scoring and cross-document verification
```

### 2. Knowledge Graph

4-signal relevance model visualized via ECharts force-directed layout:

| Signal | Weight | Description |
|--------|--------|-------------|
| Direct Link | ×3.0 | Pages linked directly |
| Source Overlap | ×4.0 | Pages sharing the same source document |
| Adamic-Adar | ×1.5 | Pages sharing common neighbors |
| Keyword Overlap | ×1.0 | Keyword intersection |

Features: Louvain community detection, cohesion scoring, graph insights (bridge nodes, isolated pages), type/community coloring toggle.

### 3. Deep Research

When knowledge gaps are identified:
- Multi-step LLM reasoning with progress visualization
- Cross-source synthesis and verification
- Auto-generated structured research reports
- Auto-ingest results into knowledge base

### 4. Contradiction Detection

- Auto-identify conflicting conclusions across sources
- Data inconsistency detection (numbers, dates, etc.)
- Severity-based classification (High/Medium/Low)
- Research direction suggestions based on existing knowledge

### 5. Multi-format Document Support

| Format | Method |
|--------|--------|
| PDF | Apache PDFBox parsing, structure recognition |
| Word (DOCX) | Apache POI parsing, preserves headings/bold/lists/tables |
| Excel (XLSX) | Apache POI parsing, multi-sheet support |
| Images | Tesseract OCR + VLM vision model |
| Text/Markdown | Direct reading, UTF-8 encoding |

### 6. Desktop Application (Tauri 2)

- **System Tray** — Show/hide window, restart backend, open data directory, quit
- **Backend Monitor** — Tri-color status indicator (green/yellow/red), JVM memory details
- **Splash Screen** — Tech-themed logo animation + loading state, 30s timeout detection
- **Global Shortcuts** — Cmd/Ctrl+Q quit, Cmd/Ctrl+W hide, Cmd/Ctrl+, settings
- **First-run Wizard** — LLM API key setup, data directory selection, sample project creation
- **Auto-update** — Built-in Tauri updater from GitHub Releases
- **Error Handling** — Global ErrorBoundary with friendly messages, auto-retry (up to 3x)
- **Single Instance** — Prevents duplicate launches, focuses existing window

## Project Structure

```
├── backend-springboot/          # Spring Boot backend
│   ├── src/main/java/
│   │   └── com/intelligence/platform/
│   │       ├── controller/      # REST API controllers
│   │       ├── service/         # Business logic layer
│   │       ├── config/          # Configuration classes
│   │       ├── entity/          # Data entities
│   │       └── mapper/          # MyBatis-Plus mappers
│   ├── pom.xml
│   └── Dockerfile
├── frontend-vue/                # Vue 3 frontend
│   ├── src/
│   │   ├── views/               # Page views
│   │   ├── components/          # Shared components
│   │   ├── api/                 # API layer
│   │   ├── composables/         # Vue composables
│   │   ├── router/              # Vue Router
│   │   └── store/               # Pinia store
│   ├── Dockerfile
│   └── nginx.conf
├── src-tauri/                   # Tauri 2 desktop shell
│   ├── src/                     # Rust backend
│   ├── icons/                   # App icons
│   └── tauri.conf.json
├── init-db/                     # Database init scripts
├── scripts/                     # Build & utility scripts
├── config/                      # Application configuration
├── docs/                        # Documentation
├── docker-compose.yml           # Docker orchestration
├── load-and-run.sh              # One-click deploy script
└── package-delivery.sh          # Delivery package builder
```

## Configuration

### LLM Configuration

Configure via Backend → System Settings → LLM Config:

- **Chat Model**: DeepSeek-Chat / GPT-4 / any OpenAI-compatible endpoint
- **Embedding Model**: BGE-M3 / text-embedding-ada-002
- **Vision Model**: For image understanding and table OCR

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MYSQL_ROOT_PASSWORD` | MySQL root password | `intel2026secure` |
| `MYSQL_DATABASE` | Database name | `intelligence_platform` |
| `JAVA_OPTS` | JVM arguments | `-Xms512m -Xmx2g` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `production` |

## Comparison with LLM Wiki

| Aspect | LLM Wiki | This Platform |
|--------|----------|---------------|
| Positioning | General personal knowledge base | Specialized intelligence platform for unmanned systems |
| Architecture | Single app (Tauri + React) | Dual app (Spring Boot + Vue 3 + Tauri) |
| Database | File-system Wiki | MySQL + MyBatis-Plus ORM |
| Frontend | React + shadcn/ui | Vue 3 + Element Plus |
| Document Types | Markdown, web pages | PDF, Word, Excel, Images(OCR), Text |
| Knowledge Format | Wiki pages (YAML frontmatter) | Structured entries + classification system |
| Project Model | Single wiki | Multi-project isolation |
| Graph Visualization | sigma.js | ECharts |
| Unique Features | Web clipper, Obsidian compatibility | Contradiction detection, research direction suggestions, report generation |

## Credits

This project is based on [Andrej Karpathy's LLM Wiki pattern](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f) and heavily inspired by [nashsu/llm_wiki](https://github.com/nashsu/llm_wiki). We extend the core ideas into a specialized platform for unmanned systems research with a focus on multi-format document processing, structured knowledge extraction, and cross-document intelligence analysis.

## License

MIT License
