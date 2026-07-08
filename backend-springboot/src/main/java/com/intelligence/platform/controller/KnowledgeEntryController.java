package com.intelligence.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.intelligence.platform.common.PageResult;
import com.intelligence.platform.entity.KnowledgeEntry;
import com.intelligence.platform.mapper.KnowledgeEntryMapper;
import com.intelligence.platform.service.ProjectContext;
import com.intelligence.platform.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识词条管理（前台"研究报告"Tab核心数据源）
 */
@RestController
@RequestMapping("/api/knowledge-entries")
@CrossOrigin(origins = "*")
public class KnowledgeEntryController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEntryController.class);

    @Autowired
    private KnowledgeEntryMapper knowledgeEntryMapper;

    @Autowired
    private ProjectContext projectContext;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private KGController kgController;

    /**
     * 分页查询词条
     * 前台"研究报告"Tab调用
     */
    @GetMapping
    public PageResult<KnowledgeEntry> list(
            @RequestParam(required = false) String library,
            @RequestParam(required = false) String entryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        LambdaQueryWrapper<KnowledgeEntry> wrapper = new LambdaQueryWrapper<>();
        // 项目隔离
        Long pid = projectContext.getCurrentProjectId();
        if (pid != null) wrapper.eq(KnowledgeEntry::getProjectId, pid);
        if (library != null && !library.isEmpty()) wrapper.eq(KnowledgeEntry::getEntryLibrary, library);
        if (entryType != null && !entryType.isEmpty()) wrapper.eq(KnowledgeEntry::getEntryType, entryType);
        if (status != null && !status.isEmpty()) wrapper.eq(KnowledgeEntry::getStatus, status);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(KnowledgeEntry::getTitle, keyword)
                    .or().like(KnowledgeEntry::getContent, keyword)
                    .or().like(KnowledgeEntry::getKeywords, keyword));
        }
        wrapper.orderByDesc(KnowledgeEntry::getCreatedAt);

        Page<KnowledgeEntry> pageObj = new Page<>(page, pageSize);
        Page<KnowledgeEntry> result = knowledgeEntryMapper.selectPage(pageObj, wrapper);
        return new PageResult<>(result.getTotal(), page, pageSize, result.getRecords());
    }

    /**
     * 按资料库统计词条数量（带项目隔离）
     */
    @GetMapping("/stats/by-library")
    public Map<String, Long> statsByLibrary() {
        Long pid = projectContext.getCurrentProjectId();

        LambdaQueryWrapper<KnowledgeEntry> reportW = new LambdaQueryWrapper<KnowledgeEntry>().eq(KnowledgeEntry::getEntryLibrary, "report");
        LambdaQueryWrapper<KnowledgeEntry> dynamicW = new LambdaQueryWrapper<KnowledgeEntry>().eq(KnowledgeEntry::getEntryLibrary, "dynamic");
        LambdaQueryWrapper<KnowledgeEntry> translationW = new LambdaQueryWrapper<KnowledgeEntry>().eq(KnowledgeEntry::getEntryLibrary, "translation");
        LambdaQueryWrapper<KnowledgeEntry> chartW = new LambdaQueryWrapper<KnowledgeEntry>().eq(KnowledgeEntry::getEntryLibrary, "chart");

        if (pid != null) {
            reportW.eq(KnowledgeEntry::getProjectId, pid);
            dynamicW.eq(KnowledgeEntry::getProjectId, pid);
            translationW.eq(KnowledgeEntry::getProjectId, pid);
            chartW.eq(KnowledgeEntry::getProjectId, pid);
        }

        long report = knowledgeEntryMapper.selectCount(reportW);
        long dynamic = knowledgeEntryMapper.selectCount(dynamicW);
        long translation = knowledgeEntryMapper.selectCount(translationW);
        long chart = knowledgeEntryMapper.selectCount(chartW);
        return Map.of("report", report, "dynamic", dynamic, "translation", translation, "chart", chart);
    }

    @GetMapping("/{id}")
    public KnowledgeEntry get(@PathVariable Long id) {
        return knowledgeEntryMapper.selectById(id);
    }

    /**
     * 手动创建词条（后台管理员）
     * 创建成功后自动更新相关图表资料的标签（智能标签功能）
     */
    @PostMapping("/")
    public Map<String, Object> create(@RequestBody KnowledgeEntry entry) {
        knowledgeEntryMapper.insert(entry);
        // 新词条创建后，自动更新相关图表资料的标签
        updateChartEntryTags(entry);
        syncKG();
        return Map.of("id", entry.getId(), "message", "创建成功");
    }

    /**
     * 新词条创建后，自动更新相关图表资料的标签
     * 根据新词条的关键词，匹配图表库中相关词条并更新其keywords字段
     * 匹配逻辑：新词条的关键词在图表词条的标题或内容中出现时，将该关键词追加到图表词条的标签中
     */
    private void updateChartEntryTags(KnowledgeEntry newEntry) {
        try {
            if (newEntry.getProjectId() == null) return;
            if ((newEntry.getKeywords() == null || newEntry.getKeywords().isEmpty())
                    && (newEntry.getTitle() == null || newEntry.getTitle().isEmpty())) {
                return;
            }

            // 查询同项目下所有图表库词条
            LambdaQueryWrapper<KnowledgeEntry> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(KnowledgeEntry::getProjectId, newEntry.getProjectId())
                   .eq(KnowledgeEntry::getEntryLibrary, "chart");
            List<KnowledgeEntry> chartEntries = knowledgeEntryMapper.selectList(wrapper);

            if (chartEntries.isEmpty()) return;

            // 收集新词条的关键词列表
            List<String> newKeywords = new ArrayList<>();
            if (newEntry.getKeywords() != null && !newEntry.getKeywords().isEmpty()) {
                for (String kw : newEntry.getKeywords().split(",")) {
                    String trimmed = kw.trim();
                    if (!trimmed.isEmpty() && trimmed.length() >= 2) {
                        newKeywords.add(trimmed);
                    }
                }
            }
            // 也将新词条标题作为匹配关键词
            if (newEntry.getTitle() != null && !newEntry.getTitle().isEmpty()) {
                newKeywords.add(newEntry.getTitle());
            }

            if (newKeywords.isEmpty()) return;

            int updatedCount = 0;
            for (KnowledgeEntry chartEntry : chartEntries) {
                // 构建图表词条的文本（标题+内容），用于关键词匹配
                String chartText = (chartEntry.getTitle() != null ? chartEntry.getTitle() : "") + " " +
                                   (chartEntry.getContent() != null ? chartEntry.getContent() : "");

                // 检查新词条的关键词是否与图表词条相关
                List<String> matchedTags = new ArrayList<>();
                for (String keyword : newKeywords) {
                    if (chartText.contains(keyword)) {
                        matchedTags.add(keyword);
                    }
                }

                if (!matchedTags.isEmpty()) {
                    String existingKeywords = chartEntry.getKeywords() != null ? chartEntry.getKeywords() : "";
                    StringBuilder merged = new StringBuilder(existingKeywords);

                    for (String tag : matchedTags) {
                        if (!existingKeywords.contains(tag)) {
                            if (merged.length() > 0) merged.append(",");
                            merged.append(tag);
                        }
                    }

                    if (!merged.toString().equals(existingKeywords)) {
                        chartEntry.setKeywords(merged.toString());
                        knowledgeEntryMapper.updateById(chartEntry);
                        updatedCount++;
                        log.info("Smart tags: updated chart entry '{}' (id={}) with tags: {}",
                                chartEntry.getTitle(), chartEntry.getId(), merged);
                    }
                }
            }

            if (updatedCount > 0) {
                log.info("Smart tags: updated {} chart entries based on new entry '{}'",
                        updatedCount, newEntry.getTitle());
            }
        } catch (Exception e) {
            // 标签更新失败不影响主流程
            log.warn("Smart tags: failed to update chart entry tags: {}", e.getMessage());
        }
    }

    /**
     * 审核词条（后台管理员）
     */
    @PutMapping("/{id}/review")
    public Map<String, Object> review(@PathVariable Long id,
                                      @RequestParam String status,
                                      @RequestParam(required = false) String reviewer) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(id);
        entry.setStatus(status);
        entry.setReviewer(reviewer);
        knowledgeEntryMapper.updateById(entry);
        syncKG();
        return Map.of("message", "审核完成");
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody KnowledgeEntry entry) {
        entry.setId(id);
        knowledgeEntryMapper.updateById(entry);
        syncKG();
        return Map.of("message", "更新成功");
    }

    /**
     * 更新词条的表格Markdown内容
     */
    @PutMapping("/{id}/table-markdown")
    public Map<String, Object> updateTableMarkdown(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String tableMarkdown = body.get("tableMarkdown");
        if (tableMarkdown == null) {
            return Map.of("status", "error", "message", "tableMarkdown is required");
        }

        KnowledgeEntry entry = knowledgeEntryMapper.selectById(id);
        if (entry == null) {
            return Map.of("status", "error", "message", "Entry not found");
        }

        entry.setTableMarkdown(tableMarkdown);
        knowledgeEntryMapper.updateById(entry);
        syncKG();

        // 重新索引向量搜索，确保编辑后的表格内容可被 Q&A 检索
        try {
            vectorSearchService.indexEntry(entry);
        } catch (Exception e) {
            // 索引失败不影响数据库更新结果，仅记录警告
            // 可通过重建索引修复
        }

        return Map.of("status", "success", "entry", entry);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        knowledgeEntryMapper.deleteById(id);
        syncKG();
        return Map.of("message", "删除成功");
    }

    /**
     * 批量审核
     */
    @PutMapping("/batch-review")
    public Map<String, Object> batchReview(@RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        String reviewer = (String) body.get("reviewer");
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        for (Number idNum : ids) {
            KnowledgeEntry entry = new KnowledgeEntry();
            entry.setId(idNum.longValue());
            entry.setStatus(status);
            entry.setReviewer(reviewer);
            knowledgeEntryMapper.updateById(entry);
        }
        syncKG();
        return Map.of("message", "批量审核完成，共" + ids.size() + "条");
    }

    private void syncKG() {
        try {
            kgController.buildGraph();
        } catch (Exception e) {
            // ignore
        }
    }
}
