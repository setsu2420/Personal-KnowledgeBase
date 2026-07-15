-- ============================================================
-- 性能索引迁移 v1.0
-- 为关键查询列添加索引，大幅提升查询性能
-- ============================================================

-- === 项目隔离核心索引 ===
DROP INDEX IF EXISTS idx_documents_project_id ON documents;
CREATE INDEX idx_documents_project_id ON documents(project_id);

DROP INDEX IF EXISTS idx_knowledge_entries_project_id ON knowledge_entries;
CREATE INDEX idx_knowledge_entries_project_id ON knowledge_entries(project_id);

DROP INDEX IF EXISTS idx_qa_records_project_id ON qa_records;
CREATE INDEX idx_qa_records_project_id ON qa_records(project_id);

DROP INDEX IF EXISTS idx_analysis_reports_project_id ON analysis_reports;
CREATE INDEX idx_analysis_reports_project_id ON analysis_reports(project_id);

DROP INDEX IF EXISTS idx_decisions_project_id ON decisions;
CREATE INDEX idx_decisions_project_id ON decisions(project_id);

DROP INDEX IF EXISTS idx_reports_project_id ON reports;
CREATE INDEX idx_reports_project_id ON reports(project_id);

DROP INDEX IF EXISTS idx_risk_alerts_project_id ON risk_alerts;
CREATE INDEX idx_risk_alerts_project_id ON risk_alerts(project_id);

DROP INDEX IF EXISTS idx_deep_researches_project_id ON deep_researches;
CREATE INDEX idx_deep_researches_project_id ON deep_researches(project_id);

-- === 会话查询索引 ===
DROP INDEX IF EXISTS idx_qa_records_session_id ON qa_records;
CREATE INDEX idx_qa_records_session_id ON qa_records(session_id);

-- === 文档关联查询 ===
DROP INDEX IF EXISTS idx_knowledge_entries_document_id ON knowledge_entries;
CREATE INDEX idx_knowledge_entries_document_id ON knowledge_entries(document_id);

-- === 词条类型 & 状态过滤 ===
DROP INDEX IF EXISTS idx_knowledge_entries_entry_type ON knowledge_entries;
CREATE INDEX idx_knowledge_entries_entry_type ON knowledge_entries(entry_type);
DROP INDEX IF EXISTS idx_knowledge_entries_status ON knowledge_entries;
CREATE INDEX idx_knowledge_entries_status ON knowledge_entries(status);

-- === 时间范围查询 ===
DROP INDEX IF EXISTS idx_documents_created_at ON documents;
CREATE INDEX idx_documents_created_at ON documents(created_at);
DROP INDEX IF EXISTS idx_qa_records_created_at ON qa_records;
CREATE INDEX idx_qa_records_created_at ON qa_records(created_at);
DROP INDEX IF EXISTS idx_knowledge_entries_created_at ON knowledge_entries;
CREATE INDEX idx_knowledge_entries_created_at ON knowledge_entries(created_at);

-- === 文件去重索引 ===
DROP INDEX IF EXISTS idx_documents_file_hash ON documents;
CREATE INDEX idx_documents_file_hash ON documents(file_hash(64));

-- === 全文搜索优化 ===
DROP INDEX IF EXISTS idx_knowledge_entries_project_library ON knowledge_entries;
CREATE INDEX idx_knowledge_entries_project_library ON knowledge_entries(project_id, entry_library);
