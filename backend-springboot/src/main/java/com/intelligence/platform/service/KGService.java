package com.intelligence.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.intelligence.platform.common.PageResult;
import com.intelligence.platform.entity.KGEdge;
import com.intelligence.platform.entity.KGNode;
import com.intelligence.platform.entity.KnowledgeEntry;
import com.intelligence.platform.mapper.KGEdgeMapper;
import com.intelligence.platform.mapper.KGNodeMapper;
import com.intelligence.platform.mapper.KnowledgeEntryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KGService {

    @Autowired
    private KGNodeMapper kgNodeMapper;
    @Autowired
    private KGEdgeMapper kgEdgeMapper;
    @Autowired
    private KnowledgeEntryMapper knowledgeEntryMapper;
    @Autowired
    private LlmService llmService;
    @Autowired
    private ProjectContext projectContext;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    // ==================== 项目级查询辅助 ====================

    List<KGNode> getProjectNodes() {
        LambdaQueryWrapper<KGNode> w = new LambdaQueryWrapper<>();
        Long pid = projectContext.getCurrentProjectId();
        if (pid != null) w.eq(KGNode::getProjectId, pid);
        return kgNodeMapper.selectList(w);
    }

    List<KGEdge> getProjectEdges() {
        LambdaQueryWrapper<KGEdge> w = new LambdaQueryWrapper<>();
        Long pid = projectContext.getCurrentProjectId();
        if (pid != null) w.eq(KGEdge::getProjectId, pid);
        return kgEdgeMapper.selectList(w);
    }

    // ==================== 节点分页查询 ====================

    public PageResult<Map<String, Object>> getNodes(int page, int pageSize) {
        Long pid = projectContext.getCurrentProjectId();

        LambdaQueryWrapper<KGNode> nodeWrapper = new LambdaQueryWrapper<>();
        if (pid != null) nodeWrapper.eq(KGNode::getProjectId, pid);

        Page<KGNode> nodePage = kgNodeMapper.selectPage(new Page<>(page, pageSize), nodeWrapper);
        List<KGNode> nodes = nodePage.getRecords();

        // 获取当前项目的知识词条，用于富化 keywords/library
        LambdaQueryWrapper<KnowledgeEntry> entryWrapper = new LambdaQueryWrapper<>();
        if (pid != null) entryWrapper.eq(KnowledgeEntry::getProjectId, pid);
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(entryWrapper);
        Map<String, KnowledgeEntry> titleToEntry = entries.stream()
                .collect(Collectors.toMap(KnowledgeEntry::getTitle, e -> e, (a, b) -> a));

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (KGNode node : nodes) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", node.getId());
            m.put("label", node.getLabel());
            m.put("nodeType", node.getNodeType());
            m.put("description", node.getDescription());
            m.put("communityId", node.getCommunityId());
            m.put("projectId", node.getProjectId());
            KnowledgeEntry entry = titleToEntry.get(node.getLabel());
            m.put("keywords", entry != null ? entry.getKeywords() : null);
            m.put("library", entry != null ? entry.getEntryLibrary() : null);
            enriched.add(m);
        }
        return new PageResult<>(nodePage.getTotal(), page, pageSize, enriched);
    }

    // ==================== 边分页查询 ====================

    public PageResult<KGEdge> getEdges(int page, int pageSize) {
        Long pid = projectContext.getCurrentProjectId();

        LambdaQueryWrapper<KGEdge> edgeWrapper = new LambdaQueryWrapper<>();
        if (pid != null) edgeWrapper.eq(KGEdge::getProjectId, pid);

        Page<KGEdge> edgePage = kgEdgeMapper.selectPage(new Page<>(page, pageSize), edgeWrapper);
        return new PageResult<>(edgePage.getTotal(), page, pageSize, edgePage.getRecords());
    }

    // ==================== 图谱数据 ====================

    public Map<String, Object> getGraphData() {
        List<KGNode> nodes = getProjectNodes();
        List<KGEdge> edges = getProjectEdges();

        // 如果节点为空，尝试自动从知识词条构建
        if (nodes.isEmpty()) {
            nodes = autoBuildGraph();
            edges = getProjectEdges();
        }

        // 获取知识词条用于富化节点信息
        Long pid = projectContext.getCurrentProjectId();
        LambdaQueryWrapper<KnowledgeEntry> entryWrapper = new LambdaQueryWrapper<>();
        if (pid != null) entryWrapper.eq(KnowledgeEntry::getProjectId, pid);
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(entryWrapper);
        Map<String, KnowledgeEntry> titleToEntry = entries.stream()
                .collect(Collectors.toMap(KnowledgeEntry::getTitle, e -> e, (a, b) -> a));

        // 计算每个节点的连接数
        Map<Long, Integer> linkCounts = new HashMap<>();
        for (KGNode n : nodes) linkCounts.put(n.getId(), 0);
        for (KGEdge e : edges) {
            linkCounts.merge(e.getSourceId(), 1, Integer::sum);
            linkCounts.merge(e.getTargetId(), 1, Integer::sum);
        }

        // 将 linkCount、keywords、entryType 附加到节点数据中
        List<Map<String, Object>> enrichedNodes = new ArrayList<>();
        for (KGNode node : nodes) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", node.getId());
            n.put("label", node.getLabel());
            n.put("nodeType", node.getNodeType());
            n.put("description", node.getDescription());
            n.put("communityId", node.getCommunityId() != null ? node.getCommunityId() : 0);
            n.put("linkCount", linkCounts.getOrDefault(node.getId(), 0));
            KnowledgeEntry entry = titleToEntry.get(node.getLabel());
            if (entry != null) {
                n.put("keywords", entry.getKeywords() != null
                        ? Arrays.asList(entry.getKeywords().split(",\\s*"))
                        : Collections.emptyList());
                n.put("entryType", entry.getEntryLibrary() != null ? entry.getEntryLibrary() :
                        entry.getEntryType() != null ? entry.getEntryType() : null);
            } else {
                n.put("keywords", Collections.emptyList());
                n.put("entryType", null);
            }
            enrichedNodes.add(n);
        }

        // 构建社区（含富化后的成员信息）
        Map<Integer, Map<String, Object>> communities = new LinkedHashMap<>();
        for (KGNode node : nodes) {
            int cid = node.getCommunityId() != null ? node.getCommunityId() : 0;
            communities.computeIfAbsent(cid, k -> {
                Map<String, Object> comm = new HashMap<>();
                comm.put("id", k);
                comm.put("members", new ArrayList<>());
                comm.put("cohesion", 0.0);
                return comm;
            });
            // 富化社区成员信息
            Map<String, Object> memberMap = new HashMap<>();
            memberMap.put("id", node.getId());
            memberMap.put("label", node.getLabel());
            KnowledgeEntry entry = titleToEntry.get(node.getLabel());
            if (entry != null && entry.getKeywords() != null) {
                memberMap.put("keywords", Arrays.asList(entry.getKeywords().split(",\\s*")));
            }
            ((List<Map<String, Object>>) communities.get(cid).get("members")).add(memberMap);
        }

        // 计算社区内聚度
        for (Map.Entry<Integer, Map<String, Object>> entry : communities.entrySet()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> members = (List<Map<String, Object>>) entry.getValue().get("members");
            Set<Long> memberIds = members.stream()
                    .map(m -> ((Number) m.get("id")).longValue())
                    .collect(Collectors.toSet());
            long internalEdges = edges.stream()
                    .filter(e -> memberIds.contains(e.getSourceId()) && memberIds.contains(e.getTargetId()))
                    .count();
            int n = memberIds.size();
            double possible = n > 1 ? (double) n * (n - 1) / 2 : 1;
            entry.getValue().put("cohesion", Math.round(internalEdges / possible * 1000.0) / 1000.0);
        }

        // 边类型统计
        Map<String, Long> edgeTypes = edges.stream()
                .collect(Collectors.groupingBy(KGEdge::getEdgeType, Collectors.counting()));

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", enrichedNodes);
        result.put("edges", edges);
        result.put("communities", new ArrayList<>(communities.values()));
        result.put("stats", Map.of(
                "node_count", nodes.size(),
                "edge_count", edges.size(),
                "community_count", communities.size(),
                "edge_types", edgeTypes
        ));
        return result;
    }

    // ==================== 洞察分析 ====================

    public List<Map<String, String>> getInsights() {
        List<KGNode> nodes = getProjectNodes();
        List<KGEdge> edges = getProjectEdges();
        List<Map<String, String>> insights = new ArrayList<>();

        Map<Long, String> nodeLabel = nodes.stream().collect(Collectors.toMap(KGNode::getId, KGNode::getLabel));
        Map<Long, Integer> nodeComm = nodes.stream().collect(Collectors.toMap(KGNode::getId, n -> n.getCommunityId() != null ? n.getCommunityId() : 0));
        Map<Long, String> nodeType = nodes.stream().collect(Collectors.toMap(KGNode::getId, KGNode::getNodeType));

        // 计算度数
        Map<Long, Integer> degrees = new HashMap<>();
        for (KGNode n : nodes) degrees.put(n.getId(), 0);
        for (KGEdge e : edges) {
            degrees.merge(e.getSourceId(), 1, Integer::sum);
            degrees.merge(e.getTargetId(), 1, Integer::sum);
        }

        // 孤立节点
        for (KGNode n : nodes) {
            if (degrees.getOrDefault(n.getId(), 0) <= 1) {
                insights.add(Map.of("type", "isolated", "title", "孤立节点: " + n.getLabel(),
                        "desc", "与图谱其余部分连接薄弱"));
            }
        }

        // 惊奇连接
        for (KGEdge e : edges) {
            if (List.of("source_overlap", "adamic_adar").contains(e.getEdgeType())) {
                Long s = e.getSourceId(), t = e.getTargetId();
                if (!Objects.equals(nodeComm.get(s), nodeComm.get(t))
                        && !Objects.equals(nodeType.get(s), nodeType.get(t))) {
                    insights.add(Map.of("type", "surprise",
                            "title", "惊奇连接: " + nodeLabel.getOrDefault(s, "?") + " ↔ " + nodeLabel.getOrDefault(t, "?"),
                            "desc", "跨社区的 " + e.getEdgeType() + " 关联"));
                }
            }
        }

        return insights.subList(0, Math.min(10, insights.size()));
    }

    // ==================== 图谱构建 ====================

    public Map<String, Object> buildGraph() {
        autoBuildGraph();
        List<KGNode> nodes = getProjectNodes();
        List<KGEdge> edges = getProjectEdges();
        return Map.of("message", "图谱构建完成",
                "node_count", nodes.size(), "edge_count", edges.size());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 自动从知识词条构建 KG（内部方法）
     * 不需要 LLM：直接从词条标题/关键词创建节点，基于共享关键词创建边
     */
    private List<KGNode> autoBuildGraph() {
        Long pid = projectContext.getCurrentProjectId();

        // 获取当前项目的知识词条 (排除图片和表格类条目)
        LambdaQueryWrapper<KnowledgeEntry> entryWrapper = new LambdaQueryWrapper<>();
        if (pid != null) entryWrapper.eq(KnowledgeEntry::getProjectId, pid);
        entryWrapper.ne(KnowledgeEntry::getEntryType, "image").ne(KnowledgeEntry::getEntryType, "table");
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(entryWrapper);

        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        // 清理旧的 KG 数据（当前项目）
        if (pid != null) {
            kgNodeMapper.delete(new LambdaQueryWrapper<KGNode>().eq(KGNode::getProjectId, pid));
            kgEdgeMapper.delete(new LambdaQueryWrapper<KGEdge>().eq(KGEdge::getProjectId, pid));
        }

        // 1. 为每个词条创建节点
        List<KGNode> nodes = new ArrayList<>();
        Map<String, KGNode> titleToNode = new HashMap<>();
        int communityCounter = 0;

        for (KnowledgeEntry entry : entries) {
            KGNode node = new KGNode();
            node.setLabel(entry.getTitle());
            node.setNodeType(entry.getEntryType() != null ? entry.getEntryType() : "concept");
            node.setDescription(entry.getContent() != null && entry.getContent().length() > 200
                    ? entry.getContent().substring(0, 200) + "..." : entry.getContent());
            node.setCommunityId(communityCounter % 3); // 简单分组
            node.setProjectId(pid);
            kgNodeMapper.insert(node);
            nodes.add(node);
            titleToNode.put(entry.getTitle(), node);
            communityCounter++;
        }

        // 2. 基于共享关键词创建边（批量插入）
        List<KGEdge> allEdges = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Set<String> kw1 = parseKeywords(entries.get(i).getKeywords());
            for (int j = i + 1; j < entries.size(); j++) {
                Set<String> kw2 = parseKeywords(entries.get(j).getKeywords());
                // 计算关键词交集
                Set<String> shared = new HashSet<>(kw1);
                shared.retainAll(kw2);
                if (!shared.isEmpty()) {
                    KGEdge edge = new KGEdge();
                    edge.setSourceId(nodes.get(i).getId());
                    edge.setTargetId(nodes.get(j).getId());
                    edge.setEdgeType("keyword_overlap");
                    edge.setWeight((double) shared.size());
                    edge.setProjectId(pid);
                    allEdges.add(edge);
                }
            }
        }
        batchInsertEdges(allEdges);

        return nodes;
    }

    private Set<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isEmpty()) return Collections.emptySet();
        return Arrays.stream(keywords.split("[,，、;；\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private void batchInsertEdges(List<KGEdge> edges) {
        if (edges.isEmpty()) return;
        int batchSize = 100;
        for (int i = 0; i < edges.size(); i += batchSize) {
            int end = Math.min(i + batchSize, edges.size());
            List<KGEdge> batch = edges.subList(i, end);
            kgEdgeMapper.insert(batch);
        }
    }
}