package com.intelligence.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intelligence.platform.entity.KnowledgeEntry;
import com.intelligence.platform.entity.QARecord;
import com.intelligence.platform.mapper.KnowledgeEntryMapper;
import com.intelligence.platform.mapper.QARecordMapper;
import com.intelligence.platform.service.ImageService;
import com.intelligence.platform.service.LlmService;
import com.intelligence.platform.service.ProjectContext;
import com.intelligence.platform.service.SettingService;
import com.intelligence.platform.service.VectorIndex;
import com.intelligence.platform.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 智能问答控制器（Graph-RAG风格）
 * 参考 llm_wiki 的 chat-panel.tsx
 * 前台"智能问答"Tab核心端点
 *
 * 流程：用户提问 → 知识图谱/词条检索相关上下文 → LLM生成回答 → 标注来源+置信度
 */
@RestController
@RequestMapping("/api/qa-chat")
@CrossOrigin(origins = "*")
public class QAChatController {

    private static final Logger log = LoggerFactory.getLogger(QAChatController.class);

    @Autowired
    private LlmService llmService;
    @Autowired
    private KnowledgeEntryMapper knowledgeEntryMapper;
    @Autowired
    private QARecordMapper qaRecordMapper;
    @Autowired
    private SettingService settingService;
    @Autowired
    private VectorSearchService vectorSearchService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private ProjectContext projectContext;

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down QA stream executor...");
        streamExecutor.shutdown();
        try {
            if (!streamExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("QA stream executor did not terminate gracefully, forcing shutdown");
                streamExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for QA stream executor to shutdown");
            streamExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("QA stream executor shut down");
    }

    /**
     * 智能问答（Graph-RAG风格 + 多模态结构化返回）
     * 1. 分类检索文本/表格/图片
     * 2. 调用LLM生成回答（文字部分）
     * 3. 结构化返回：text → tables → images，每个携带来源标注
     */
    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "");
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());

        if (question.isEmpty()) {
            return Map.of("answer", "请输入问题", "confidence", 0.0, "sources", List.of(),
                    "tables", List.of(), "images", List.of());
        }

        try {
            // 1. 分类检索：文本 + 表格 + 图片
            int maxEntries = settingService.getInt("qa_max_entries", 10);
            int imageTopK = settingService.getInt("qa_image_topK", 5);
            float imageThreshold = settingService.getFloat("qa_image_threshold", 0.2f);
            int tableTopK = settingService.getInt("qa_table_topK", 3);
            float tableThreshold = settingService.getFloat("qa_table_threshold", 0.35f);

            // 文本检索（含表格）
            List<KnowledgeEntry> relevantEntries = searchRelevantEntries(question, maxEntries);
            String context = buildContext(relevantEntries);

            log.info("问答图片/表格检索参数 - imageTopK={}, imageThreshold={}, tableTopK={}, tableThreshold={}",
                    imageTopK, imageThreshold, tableTopK, tableThreshold);

            // 图片检索（先用阈值筛选，再用 Top-K 截取）；topK=0 时不返回
            List<Map<String, Object>> imageResults = imageTopK > 0
                    ? searchImages(question, imageTopK, imageThreshold) : List.of();

            // 表格检索（先用阈值筛选，再用 Top-K 截取）；topK=0 时不返回
            List<Map<String, Object>> tableResults = tableTopK > 0
                    ? searchTables(question, tableTopK, tableThreshold) : List.of();

            // 去重：移除与表格重复的图片（同一ID不应同时出现在图片和表格中）
            imageResults = deduplicateImages(imageResults, tableResults);

            log.info("图片搜索结果: {} 个", imageResults.size());
            log.info("表格搜索结果: {} 个", tableResults.size());

            // 2. 构建系统提示词（表格直接嵌入回答中，不再单独展示）
            String tableContext = buildTableContext(tableResults);
            String systemPrompt = """
                    你是一个专业的智能情报分析助手。请基于以下知识库内容回答用户的问题。

                    引用规则（严格遵守）：
                    1. 引用信息时，使用方括号编号标注来源，例如 [1]、[2]
                    2. 不要在正文中写出来源名称或文件名，只用编号
                    3. 不要在回答中嵌入"来源：xxx"等文字
                    4. 在回答最末尾添加一个隐藏注释，格式为：<!-- cited: 1, 2, 3 -->，列出你引用的所有编号

                    回答格式要求：
                    1. 首先用文字回答核心问题，引用相关数据
                    2. 如果有相关表格数据，必须直接用 Markdown 表格格式嵌入到回答正文中，不要省略
                    3. 表格必须按顺序编号，格式为 **表01**、**表02**...，放在表格上方作为标题
                    4. 如果有相关图表或图片，在文字中说明"参见相关图表"

                    其他规则：
                    1. 优先使用知识库中的信息回答
                    2. 如果知识库中没有相关信息，明确说明
                    3. 回答要准确、结构化、有深度
                    4. 使用中文回答

                    知识库内容：
                    """ + context + tableContext;

            // 3. 调用LLM
            String answer = llmService.chatWithActive(systemPrompt, question);

            // 3.5 清理回答：移除 <!-- cited: ... --> 隐藏注释和内联来源标记
            answer = cleanAnswer(answer);

            // 4. 计算置信度
            double confidence = relevantEntries.isEmpty() ? 0.3 : Math.min(0.95, 0.5 + relevantEntries.size() * 0.05);

            // 5. 构建来源列表（带编号，供前端引用面板使用）
            List<Map<String, Object>> sources = new ArrayList<>();
            int srcIdx = 0;
            for (KnowledgeEntry e : relevantEntries) {
                if (srcIdx >= 10) break; // 最多10个来源
                Map<String, Object> src = new LinkedHashMap<>();
                src.put("index", srcIdx + 1);
                src.put("entry_id", e.getId());
                src.put("title", e.getTitle());
                src.put("library", e.getEntryLibrary() != null ? e.getEntryLibrary() : "");
                src.put("media_type", e.getMediaType() != null ? e.getMediaType() : "text");
                src.put("source_name", e.getSourceName() != null ? e.getSourceName() : "");
                src.put("source_origin", e.getSourceOrigin() != null ? e.getSourceOrigin() : "");
                src.put("content", e.getContent() != null ? e.getContent().substring(0, Math.min(e.getContent().length(), 150)) + "..." : "");
                sources.add(src);
                srcIdx++;
            }

            // 6. 保存问答记录
            QARecord record = new QARecord();
            record.setProjectId(projectContext.getCurrentProjectId());
            record.setQuestion(question);
            record.setAnswer(answer);
            record.setConfidence(confidence);
            record.setSources(objectMapper.writeValueAsString(sources));
            record.setUserName("分析人员");
            record.setSessionId(sessionId);
            record.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            record.setCategory("");
            qaRecordMapper.insert(record);

            // 7. 结构化返回：text → tables → images
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("answer", answer);
            response.put("confidence", confidence);
            response.put("sources", sources);
            response.put("tables", tableResults);
            response.put("images", imageResults);
            response.put("entry_count", relevantEntries.size());
            response.put("session_id", sessionId);
            return response;

        } catch (Exception e) {
            return Map.of(
                    "answer", "抱歉，系统暂时无法处理您的请求: " + e.getMessage(),
                    "confidence", 0.0,
                    "sources", List.of(),
                    "tables", List.of(),
                    "images", List.of());
        }
    }

    private static final ExecutorService streamExecutor = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "qa-stream");
        t.setDaemon(true);
        return t;
    });
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式问答端点（SSE）
     * 前端通过 fetch + ReadableStream 消费
     *
     * SSE 事件格式：
     *   event: meta    data: {"sources":[...],"tables":[...],"images":[...],"sessionId":"..."}
     *   event: delta   data: {"text":"..."}
     *   event: done    data: {"answer":"完整回答","confidence":0.95}
     *   event: error   data: {"message":"错误信息"}
     */
    @PostMapping(value = "/ask/stream", produces = "text/event-stream")
    public SseEmitter askStream(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        String sessionId = (String) body.get("sessionId");
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟超时
        final String finalSessionId = sessionId;
        final org.springframework.web.context.request.RequestAttributes requestAttributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();

        streamExecutor.execute(() -> {
            if (requestAttributes != null) {
                org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(requestAttributes);
            }
            try {
                // 1. 知识检索
                int maxEntries = settingService.getInt("qa_max_entries", 20);
                int imageTopK = settingService.getInt("qa_image_topK", 5);
                float imageThreshold = settingService.getFloat("qa_image_threshold", 0.2f);
                int tableTopK = settingService.getInt("qa_table_topK", 3);
                float tableThreshold = settingService.getFloat("qa_table_threshold", 0.35f);

                List<KnowledgeEntry> relevantEntries = searchRelevantEntries(question, maxEntries);
                String context = buildContext(relevantEntries);

                List<Map<String, Object>> imageResults = imageTopK > 0
                        ? searchImages(question, imageTopK, imageThreshold) : List.of();
                List<Map<String, Object>> tableResults = tableTopK > 0
                        ? searchTables(question, tableTopK, tableThreshold) : List.of();
                imageResults = deduplicateImages(imageResults, tableResults);

                // 2. 发送 meta 事件（来源、表格、图片信息）
                String tableContext = buildTableContext(tableResults);
                String systemPrompt = """
                        你是一个专业的智能情报分析助手。请基于以下知识库内容回答用户的问题。

                        引用规则（严格遵守）：
                        1. 引用信息时，使用方括号编号标注来源，例如 [1]、[2]
                        2. 不要在正文中写出来源名称或文件名，只用编号
                        3. 不要在回答中嵌入"来源：xxx"等文字
                        4. 在回答最末尾添加一个隐藏注释，格式为：<!-- cited: 1, 2, 3 -->，列出你引用的所有编号

                        回答格式要求：
                        1. 先进行文字分析，引用相关文献
                        2. 如果有相关表格数据，必须直接用 Markdown 表格格式嵌入到回答正文中，不要省略
                        3. 表格必须按顺序编号，格式为 **表01**、**表02**...，放在表格上方作为标题
                        4. 如果有相关图片，说明"参见相关图表"，不要在文字中嵌入图片引用
                        5. 如果知识库中没有相关信息，请诚实说明

                        知识库上下文：
                        """ + context + tableContext;

                // 构建丰富的来源列表（与非流式接口格式一致）
                List<Map<String, Object>> sources = buildSourceList(relevantEntries);

                Map<String, Object> meta = new HashMap<>();
                meta.put("sessionId", finalSessionId);
                meta.put("sources", sources);
                meta.put("images", imageResults);
                meta.put("tables", tableResults);
                emitter.send(SseEmitter.event().name("meta").data(objectMapper.writeValueAsString(meta)));

                // 3. 流式调用 LLM
                StringBuilder fullAnswer = new StringBuilder();
                llmService.streamChatWithActive(systemPrompt, question, chunk -> {
                    try {
                        fullAnswer.append(chunk);
                        Map<String, String> delta = Map.of("text", chunk);
                        emitter.send(SseEmitter.event().name("delta")
                                .data(objectMapper.writeValueAsString(delta)));
                    } catch (Exception e) {
                        log.warn("发送流式数据失败: {}", e.getMessage());
                    }
                });

                // 4. 清理回答，计算置信度
                String cleanedAnswer = cleanAnswer(fullAnswer.toString());
                double confidence = relevantEntries.isEmpty() ? 0.3
                        : Math.min(0.95, 0.5 + relevantEntries.size() * 0.05);

                // 5. 先保存问答记录到数据库（必须在发送 done 事件之前完成，
                //    避免前端收到 done 后立即调用 loadSessions() 时记录尚未写入的竞态条件）
                try {
                    QARecord record = new QARecord();
                    record.setProjectId(projectContext.getCurrentProjectId());
                    record.setQuestion(question);
                    record.setAnswer(cleanedAnswer);
                    record.setConfidence(confidence);
                    record.setSessionId(finalSessionId);
                    record.setSources(objectMapper.writeValueAsString(buildSourceList(relevantEntries)));
                    record.setUserName("分析人员");
                    record.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    qaRecordMapper.insert(record);
                    log.debug("问答记录已保存: sessionId={}", finalSessionId);
                } catch (Exception e) {
                    log.warn("保存问答记录失败: {}", e.getMessage());
                }

                // 6. 发送 done 事件（数据库已写入，前端可安全刷新会话列表）
                Map<String, Object> doneData = new HashMap<>();
                doneData.put("answer", cleanedAnswer);
                doneData.put("confidence", Math.round(confidence * 100.0) / 100.0);
                doneData.put("sources", sources);
                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(doneData)));

                emitter.complete();

            } catch (Exception e) {
                try {
                    Map<String, String> err = Map.of("message", e.getMessage());
                    emitter.send(SseEmitter.event().name("error")
                            .data(objectMapper.writeValueAsString(err)));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            } finally {
                org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            }
        });

        return emitter;
    }

    /**
     * 构建来源列表（用于保存问答记录）
     * 返回与流式接口一致的丰富格式
     */
    private List<Map<String, Object>> buildSourceList(List<KnowledgeEntry> entries) {
        List<Map<String, Object>> sources = new ArrayList<>();
        int srcIdx = 0;
        for (KnowledgeEntry e : entries) {
            if (srcIdx >= 10) break;
            Map<String, Object> src = new LinkedHashMap<>();
            src.put("index", srcIdx + 1);
            src.put("entry_id", e.getId());
            src.put("title", e.getTitle());
            src.put("library", e.getEntryLibrary() != null ? e.getEntryLibrary() : "");
            src.put("media_type", e.getMediaType() != null ? e.getMediaType() : "text");
            src.put("source_name", e.getSourceName() != null ? e.getSourceName() : "");
            src.put("source_origin", e.getSourceOrigin() != null ? e.getSourceOrigin() : "");
            src.put("content", e.getContent() != null ? e.getContent().substring(0, Math.min(e.getContent().length(), 150)) + "..." : "");
            sources.add(src);
            srcIdx++;
        }
        return sources;
    }

    /**
     * 去重：移除与表格结果ID重复的图片，确保每个图表只出现一次
     */
    private List<Map<String, Object>> deduplicateImages(
            List<Map<String, Object>> imageResults, List<Map<String, Object>> tableResults) {
        if (imageResults.isEmpty() || tableResults.isEmpty()) return imageResults;
        Set<Object> tableIds = tableResults.stream()
                .map(t -> t.get("id"))
                .collect(Collectors.toSet());
        return imageResults.stream()
                .filter(img -> !tableIds.contains(img.get("id")))
                .collect(Collectors.toList());
    }

    /**
     * 检索相关图片（Top-K + 阈值混合策略，按分数降序，优先返回高分图片）
     */
    private List<Map<String, Object>> searchImages(String question, int topK, float threshold) {
        try {
            List<VectorIndex.SearchResult> results = vectorSearchService.searchWithFilter(
                    question, topK, threshold, "image");
            return results.stream()
                    .sorted((a, b) -> Float.compare(b.score(), a.score()))
                    .limit(topK)
                    .map(r -> {
                        Map<String, Object> img = new LinkedHashMap<>();
                        img.put("id", r.id());
                        img.put("score", r.score());
                        img.put("caption", r.metadata().getOrDefault("content", ""));
                        img.put("media_path", r.metadata().getOrDefault("mediaPath", ""));
                        img.put("title", r.metadata().getOrDefault("title", ""));
                        img.put("source_origin", r.metadata().getOrDefault("sourceOrigin", ""));
                        img.put("source_name", r.metadata().getOrDefault("sourceName", ""));
                        return img;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("图片检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 检索相关表格（按分数降序，优先返回高分表格）
     */
    private List<Map<String, Object>> searchTables(String question, int topK, float threshold) {
        try {
            List<VectorIndex.SearchResult> results = vectorSearchService.searchWithFilter(
                    question, topK, threshold, "table");
            return results.stream()
                    .sorted((a, b) -> Float.compare(b.score(), a.score()))
                    .limit(topK)
                    .map(r -> {
                        Map<String, Object> table = new LinkedHashMap<>();
                        table.put("id", r.id());
                        table.put("score", r.score());
                        table.put("title", r.metadata().getOrDefault("title", ""));
                        // 确保 table_markdown 是标准 Markdown 格式（如果是 HTML 格式则转换）
                        String tableMd = r.metadata().getOrDefault("tableMarkdown", "");
                        if (tableMd != null && tableMd.toLowerCase().contains("<table")) {
                            tableMd = imageService.htmlTableToMarkdown(tableMd);
                        }
                        table.put("table_markdown", tableMd);
                        table.put("source_origin", r.metadata().getOrDefault("sourceOrigin", ""));
                        table.put("source_name", r.metadata().getOrDefault("sourceName", ""));
                        return table;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("表格检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 检索相关知识词条
     * 优先使用向量搜索（语义相似度），回退到关键词搜索
     */
    private List<KnowledgeEntry> searchRelevantEntries(String question, int maxResults) {
        Long projectId = projectContext.getCurrentProjectId();
        // 优先使用向量搜索（FAISS IndexFlatIP 等价实现）
        try {
            List<VectorIndex.SearchResult> vectorResults = vectorSearchService.search(question, maxResults);
            if (!vectorResults.isEmpty()) {
                log.info("向量搜索命中 {} 条结果", vectorResults.size());
                // 根据向量搜索结果ID获取完整的知识词条
                List<Long> ids = vectorResults.stream()
                        .map(VectorIndex.SearchResult::id)
                        .toList();
                if (!ids.isEmpty()) {
                    List<KnowledgeEntry> entries = knowledgeEntryMapper.selectBatchIds(ids);
                    // 按项目隔离过滤
                    if (projectId != null) {
                        entries = entries.stream()
                                .filter(e -> projectId.equals(e.getProjectId()))
                                .collect(Collectors.toList());
                    }
                    if (!entries.isEmpty()) {
                        return entries;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("向量搜索失败，回退到关键词搜索: {}", e.getMessage());
        }

        // 回退：关键词搜索（增强中文混合查询支持）
        String[] rawKeywords = Arrays.stream(question.split("[\\s,，。？！、；：]+"))
                .filter(kw -> kw.length() >= 2)
                .toArray(String[]::new);

        if (rawKeywords.length == 0) {
            return List.of();
        }

        // 扩展关键词：对中英文混合查询，额外提取英文/数字部分
        // 例："什么是OPD" → 添加 "OPD"；"MINILLM是什么" → 添加 "MINILLM"
        java.util.List<String> expandedKwList = new java.util.ArrayList<>();
        java.util.regex.Pattern engPattern = java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{1,}");
        for (String kw : rawKeywords) {
            expandedKwList.add(kw);
            // 提取连续英文/数字部分作为独立关键词
            java.util.regex.Matcher m = engPattern.matcher(kw);
            while (m.find()) {
                String engPart = m.group();
                if (!engPart.equals(kw) && !expandedKwList.contains(engPart)) {
                    expandedKwList.add(engPart);
                }
            }
            // 提取纯中文部分（移除英文和数字后）
            String cnPart = kw.replaceAll("[A-Za-z0-9._-]+", "").trim();
            if (cnPart.length() >= 2 && !cnPart.equals(kw) && !expandedKwList.contains(cnPart)) {
                expandedKwList.add(cnPart);
            }
        }
        // 去除常见中文疑问前缀/后缀，生成干净的搜索词
        java.util.List<String> finalKwList = new java.util.ArrayList<>();
        String[] questionAffixes = {"什么是", "是什么", "如何", "怎么", "为什么", "什么", "怎样", "怎么样"};
        for (String kw : expandedKwList) {
            String cleaned = kw;
            // 移除疑问前缀
            for (String affix : questionAffixes) {
                if (cleaned.startsWith(affix) && cleaned.length() > affix.length()) {
                    cleaned = cleaned.substring(affix.length());
                }
                if (cleaned.endsWith(affix) && cleaned.length() > affix.length()) {
                    cleaned = cleaned.substring(0, cleaned.length() - affix.length());
                }
            }
            cleaned = cleaned.trim();
            if (cleaned.length() >= 2 && !finalKwList.contains(cleaned)) {
                finalKwList.add(cleaned);
            } else if (!finalKwList.contains(kw)) {
                finalKwList.add(kw);
            }
        }

        String[] keywords = finalKwList.toArray(new String[0]);
        log.info("关键词搜索 - 原始: {}, 扩展后: {}", 
                Arrays.toString(rawKeywords), Arrays.toString(keywords));

        LambdaQueryWrapper<KnowledgeEntry> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) wrapper.eq(KnowledgeEntry::getProjectId, projectId);
        wrapper.in(KnowledgeEntry::getStatus, "approved", "pending")
                .and(w -> {
                    for (String kw : keywords) {
                        w.or()
                                .like(KnowledgeEntry::getTitle, kw)
                                .or()
                                .like(KnowledgeEntry::getContent, kw)
                                .or()
                                .like(KnowledgeEntry::getKeywords, kw);
                    }
                })
                .orderByDesc(KnowledgeEntry::getConfidence)
                .last("LIMIT " + maxResults);

        return knowledgeEntryMapper.selectList(wrapper);
    }

    /**
     * 清理回答文本：移除隐藏注释和内联来源标记
     */
    private String cleanAnswer(String answer) {
        if (answer == null) return "";
        // 移除 <!-- cited: ... --> 隐藏注释
        answer = answer.replaceAll("<!--\\s*cited:.*?-->", "");
        // 移除内联来源标记，如 (来源：xxx) 或 （来源：xxx）
        answer = answer.replaceAll("[（(]来源[：:][^）)]*[）)]", "");
        // 移除尾部多余空行
        return answer.trim();
    }

    /**
     * 构建上下文文本
     */
    private String buildContext(List<KnowledgeEntry> entries) {
        if (entries.isEmpty()) {
            return "（知识库中暂无相关内容）";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            KnowledgeEntry e = entries.get(i);
            String entryType = e.getEntryType() != null ? e.getEntryType() : "concept";
            sb.append(String.format("[%d] 【%s】%s\n", i + 1, entryType, e.getTitle()));
            if (e.getContent() != null) {
                sb.append(e.getContent()).append("\n");
            }
            // 如果条目有 tableMarkdown，将其内容也加入上下文
            if (e.getTableMarkdown() != null && !e.getTableMarkdown().isEmpty()) {
                String tableMd = e.getTableMarkdown();
                // 如果是 HTML 格式则转换为 Markdown
                if (tableMd.toLowerCase().contains("<table")) {
                    tableMd = imageService.htmlTableToMarkdown(tableMd);
                }
                sb.append("[table] ").append(e.getTitle()).append("\n");
                sb.append(tableMd).append("\n");
            }
            if (e.getKeywords() != null) {
                sb.append("关键词: ").append(e.getKeywords()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 将表格搜索结果构建为上下文文本，供 LLM 嵌入回答中
     */
    private String buildTableContext(List<Map<String, Object>> tableResults) {
        if (tableResults == null || tableResults.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- 相关表格数据（请直接用 Markdown 表格格式嵌入回答中）---\n");
        for (Map<String, Object> tbl : tableResults) {
            String title = (String) tbl.getOrDefault("title", "");
            String markdown = (String) tbl.getOrDefault("table_markdown", "");
            if (!markdown.isEmpty()) {
                if (!title.isEmpty()) {
                    sb.append("【").append(title).append("】\n");
                }
                sb.append(markdown).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取会话历史（按项目隔离，按最近消息时间倒序排列）
     */
    @GetMapping("/sessions")
    public List<Map<String, Object>> listSessions() {
        Long projectId = projectContext.getCurrentProjectId();
        LambdaQueryWrapper<QARecord> sessionWrapper = new LambdaQueryWrapper<QARecord>()
                .ne(QARecord::getSessionId, "")
                .isNotNull(QARecord::getSessionId);
        if (projectId != null) {
            sessionWrapper.eq(QARecord::getProjectId, projectId);
        }
        sessionWrapper.orderByAsc(QARecord::getCreatedAt);

        List<QARecord> all = qaRecordMapper.selectList(sessionWrapper);

        return all.stream()
                .filter(r -> r.getSessionId() != null && !r.getSessionId().isEmpty())
                .collect(Collectors.groupingBy(QARecord::getSessionId))
                .entrySet().stream()
                .map(e -> {
                    Map<String, Object> session = new LinkedHashMap<>();
                    session.put("session_id", e.getKey());
                    session.put("title", e.getValue().get(0).getQuestion());
                    session.put("first_msg", e.getValue().get(0).getCreatedAt());
                    // 最后一条消息时间，用于排序
                    session.put("last_msg", e.getValue().get(e.getValue().size() - 1).getCreatedAt());
                    session.put("msg_count", e.getValue().size());
                    return session;
                })
                // 按最近消息时间倒序排列（最新的在最前面）
                .sorted((a, b) -> {
                    String timeA = (String) a.get("last_msg");
                    String timeB = (String) b.get("last_msg");
                    return timeB.compareTo(timeA);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取指定会话的消息（按项目隔离）
     */
    @GetMapping("/session/{sessionId}")
    public List<QARecord> getSession(@PathVariable String sessionId) {
        Long projectId = projectContext.getCurrentProjectId();
        LambdaQueryWrapper<QARecord> wrapper = new LambdaQueryWrapper<QARecord>()
                .eq(QARecord::getSessionId, sessionId);
        if (projectId != null) {
            wrapper.eq(QARecord::getProjectId, projectId);
        }
        wrapper.orderByAsc(QARecord::getCreatedAt);
        return qaRecordMapper.selectList(wrapper);
    }

    /**
     * 删除指定会话的所有消息记录（按项目隔离）
     */
    @DeleteMapping("/session/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        Long projectId = projectContext.getCurrentProjectId();
        LambdaQueryWrapper<QARecord> wrapper = new LambdaQueryWrapper<QARecord>()
                .eq(QARecord::getSessionId, sessionId);
        if (projectId != null) {
            wrapper.eq(QARecord::getProjectId, projectId);
        }
        int deleted = qaRecordMapper.delete(wrapper);
        return Map.of("message", "已删除会话 " + sessionId + "（" + deleted + " 条记录）");
    }
}
