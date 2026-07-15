package com.intelligence.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligence.platform.entity.Document;
import com.intelligence.platform.entity.KnowledgeEntry;
import com.intelligence.platform.entity.Setting;
import com.intelligence.platform.mapper.DocumentMapper;
import com.intelligence.platform.mapper.KnowledgeEntryMapper;
import com.intelligence.platform.mapper.SettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 双步 CoT（Chain-of-Thought）文档摄入服务
 * 参考 llm_wiki 的 Two-Step Ingest 模式：
 * 
 * Step 1: LLM 分析文档结构 → 识别高价值主题、关键实体、论点分层
 * Step 2: LLM 基于分析结果生成结构化词条（JSON 数组）
 * 
 * 优势：
 * - 比单步抽取质量更高（LLM 先"理解"再"生成"）
 * - 支持 SHA256 缓存，避免重复分析
 * - 增量更新：只处理新增/变更的词条
 */
@Service
public class TwoStepIngestService {

    private static final Logger log = LoggerFactory.getLogger(TwoStepIngestService.class);

    @Autowired
    private LlmService llmService;

    @Autowired
    private KnowledgeEntryMapper knowledgeEntryMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private SettingMapper settingMapper;

    @Autowired
    private VectorSearchService vectorSearchService;

    private final ObjectMapper mapper = new ObjectMapper();

    private boolean isAutoApprove() {
        Setting s = settingMapper.selectById("auto_approve_entries");
        return s != null && "true".equals(s.getValue());
    }

    /**
     * Two-Step CoT 摄入结果
     */
    public record IngestResult(
            int totalEntries,
            int newEntries,
            int updatedEntries,
            List<KnowledgeEntry> entries,
            String analysisSummary  // Step 1 的结构分析摘要
    ) {}

    /**
     * 执行双步 CoT 摄入
     * @param text 文档正文（Markdown 或纯文本）
     * @param doc 文档实体
     * @param library 目标库（report/dynamic/translation/chart）
     * @return 摄入结果
     */
    public IngestResult ingestTwoStep(String text, Document doc, String library) throws Exception {
        log.info("Two-step CoT ingest for doc '{}': {} chars", doc.getTitle(), text.length());

        // === Step 1: 结构分析 ===
        String analysis = analyzeStructure(text, doc);
        log.info("Step 1 analysis complete: {} chars", analysis.length());

        // === Step 2: 词条生成 ===
        List<KnowledgeEntry> entries = generateEntries(text, analysis, doc, library);
        log.info("Step 2 generated {} entries", entries.size());

        // 保存词条
        int newEntries = 0, updatedEntries = 0;
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String autoStatus = isAutoApprove() ? "approved" : "pending";

        for (KnowledgeEntry entry : entries) {
            entry.setDocumentId(doc.getId());
            entry.setSourceName(doc.getTitle());
            entry.setEntryLibrary(library);
            entry.setProjectId(doc.getProjectId());
            entry.setStatus(autoStatus);
            entry.setCreatedAt(now);

            // 检查是否已有相同标题的词条（更新而非新增）
            KnowledgeEntry existing = findExisting(entry.getTitle(), doc.getId());
            if (existing != null) {
                entry.setId(existing.getId());
                knowledgeEntryMapper.updateById(entry);
                updatedEntries++;
            } else {
                knowledgeEntryMapper.insert(entry);
                newEntries++;
            }
        }

        // 重新索引向量
        for (KnowledgeEntry entry : entries) {
            try {
                vectorSearchService.indexEntry(entry);
            } catch (Exception e) {
                log.warn("Vector index failed for entry {}: {}", entry.getId(), e.getMessage());
            }
        }

        return new IngestResult(entries.size(), newEntries, updatedEntries, entries, analysis);
    }

    /**
     * Step 1: LLM 分析文档结构
     * 识别：核心主题、关键章节、重要实体、论点分层、知识盲区
     */
    private String analyzeStructure(String text, Document doc) throws Exception {
        // 截断过长的文本（最多 50000 字符用于分析）
        String truncated = text.length() > 50000 ? text.substring(0, 50000) + "\n\n[文本已截断]..." : text;

        String systemPrompt = """
                你是一个专业的知识文档分析师（参考 GitHub 开源项目 llm_wiki 的分析框架）。
                你的任务是分析给定文档的结构和内容，为后续的知识词条抽取提供指导。

                【分析维度】：
                1. **核心主题**：文档主要讨论了哪些主题？列出 3-8 个核心主题
                2. **关键章节**：文档的结构是什么？列出主要章节及其主题
                3. **重要实体**：文档中提到了哪些重要实体（机构、人物、平台、项目等）？
                4. **论点层级**：主要论点是什么？有哪些支撑论据？
                5. **知识盲区**：文档中的信息有什么局限或未覆盖的方面？
                6. **高价值词条建议**：哪些内容最应该被抽取为知识词条？列出 5-10 个建议

                【输出格式】：
                以结构化的 Markdown 格式输出你的分析，使用二级标题分隔各个维度。
                在末尾的「词条建议」部分，用 JSON 数组列出推荐词条标题和类型，例如：
                {"title": "分级诊疗", "type": "concept", "priority": "high"}
                """;

        return llmService.chatWithActive(systemPrompt, 
                "请分析以下文档（标题：" + doc.getTitle() + "）：\n\n" + truncated);
    }

    /**
     * Step 2: LLM 基于分析结果生成结构化词条
     */
    private List<KnowledgeEntry> generateEntries(String text, String analysis, Document doc, String library) throws Exception {
        // 截断文本（最多 40000 字符用于生成）
        String truncated = text.length() > 40000 ? text.substring(0, 40000) : text;

        String systemPrompt = """
                你是一个专业的知识词条生成助手（参考 llm_wiki 的词条定义）。
                基于对文档的结构分析和原始文本，生成高质量的结构化知识词条。

                【词条类型】必须使用以下之一：
                - concept（核心概念、技术术语、定义）
                - entity（机构、平台、实体名称）
                - thesis（核心论点、学术观点）
                - methodology（研究方法、分析框架）
                - finding（核心发现、结论、数据总结）
                - comparison（横向对比、多维度对比分析）
                - synthesis（综合论述、跨领域融合）

                【词条质量要求】：
                1. 每个词条的 content 字段应包含详细的 Markdown 格式正文（至少 100 字）
                2. 词条应有明确的独立价值，可以脱离原文档被理解
                3. 优先从分析报告中「高价值词条建议」中选择主题
                4. 尽量覆盖文档中的所有重要知识点

                【输出格式】：
                只返回 JSON 数组，不包含 ```json``` 标记：
                [{"title":"...","type":"concept","content":"...","keywords":"tag1,tag2","confidence":0.95,"summary":"一句话摘要"}]
                """;

        // 智能标签：查询已有词条作为LLM参考上下文，优先复用已有标签体系
        String existingContext = buildExistingContext(doc.getProjectId(), library);
        String effectivePrompt = systemPrompt + existingContext;

        String userPrompt = String.format("""
                文档标题：%s
                
                ===== 结构分析报告 =====
                %s
                
                ===== 原始文本 =====
                %s
                
                请基于以上分析报告和原始文本，生成知识词条JSON数组。
                """, doc.getTitle(), analysis, truncated);

        String response = llmService.chatWithExtract(effectivePrompt, userPrompt);
        return parseEntries(response, doc);
    }

    /**
     * 构建已有词条上下文（智能标签参考）
     */
    private String buildExistingContext(Long projectId, String library) {
        try {
            var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeEntry>();
            if (projectId != null) wrapper.eq(KnowledgeEntry::getProjectId, projectId);
            wrapper.select(KnowledgeEntry::getTitle, KnowledgeEntry::getEntryType, KnowledgeEntry::getKeywords);
            wrapper.last("LIMIT 50");
            var existing = knowledgeEntryMapper.selectList(wrapper);

            if (existing.isEmpty()) return "";

            StringBuilder sb = new StringBuilder("\n\n【已有词条参考上下文】\n参考以下已有词条的标签体系，尽量保持一致性：\n");
            for (var e : existing) {
                sb.append("- ").append(e.getTitle())
                        .append(" [").append(e.getEntryType()).append("]");
                if (e.getKeywords() != null && !e.getKeywords().isBlank()) {
                    sb.append(" tags: ").append(e.getKeywords());
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 查找已存在的词条
     */
    private KnowledgeEntry findExisting(String title, Long documentId) {
        try {
            var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeEntry>()
                    .eq(KnowledgeEntry::getTitle, title)
                    .eq(KnowledgeEntry::getDocumentId, documentId);
            return knowledgeEntryMapper.selectOne(wrapper);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 LLM 返回的 JSON 数组为 KnowledgeEntry 列表
     */
    @SuppressWarnings("unchecked")
    private List<KnowledgeEntry> parseEntries(String json, Document doc) {
        List<KnowledgeEntry> entries = new ArrayList<>();
        try {
            // 清理可能的 Markdown 标记
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }

            List<Map<String, Object>> rawList = mapper.readValue(cleaned,
                    new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> item : rawList) {
                KnowledgeEntry entry = new KnowledgeEntry();
                entry.setTitle(safeString(item.get("title")));
                entry.setEntryType(safeString(item.get("type"), "concept"));
                // 验证类型
                Set<String> validTypes = Set.of("concept", "entity", "thesis", "methodology", 
                        "finding", "comparison", "synthesis");
                if (!validTypes.contains(entry.getEntryType())) {
                    entry.setEntryType("concept");
                }
                entry.setContent(safeString(item.get("content")));
                entry.setKeywords(safeString(item.get("keywords")));
                entry.setConfidence(parseDouble(item.get("confidence"), 0.85));
                entry.setMediaType("text");

                // 处理 summary/description 字段
                String summary = safeString(item.get("summary"));
                if (summary.isBlank()) summary = safeString(item.get("description"));
                entry.setDescription(summary);

                // 处理相关词条
                Object related = item.get("related");
                if (related instanceof List) {
                    try { entry.setRelated(mapper.writeValueAsString(related)); } catch (Exception ignored) {}
                }

                entries.add(entry);
            }
        } catch (Exception e) {
            log.warn("Failed to parse CoT entries JSON: {}", e.getMessage());
        }
        return entries;
    }

    private String safeString(Object val) { return safeString(val, ""); }
    private String safeString(Object val, String defaultVal) {
        return val != null ? val.toString().trim() : defaultVal;
    }
    private double parseDouble(Object val, double defaultVal) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (Exception ignored) {}
        }
        return defaultVal;
    }
}
