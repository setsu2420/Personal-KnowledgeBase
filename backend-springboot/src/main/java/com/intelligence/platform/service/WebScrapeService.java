package com.intelligence.platform.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 网页抓取与内容提取服务（参考 llm_wiki Chrome Web Clipper / Readability.js）
 * 
 * 功能：
 * 1. 抓取网页 HTML
 * 2. 使用 Readability-style 算法提取正文内容
 * 3. 提取元数据（标题、作者、日期、描述）
 * 4. 转换为 Markdown 格式
 * 5. SHA256 缓存避免重复抓取
 */
@Service
public class WebScrapeService {

    private static final Logger log = LoggerFactory.getLogger(WebScrapeService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // 非正文内容标签/类名模式
    private static final Set<String> NON_CONTENT_TAGS = Set.of(
            "nav", "header", "footer", "aside", "script", "style", "noscript",
            "iframe", "form", "button", "input", "select", "textarea"
    );

    private static final Pattern NON_CONTENT_CLASSES = Pattern.compile(
            "(nav|sidebar|footer|header|menu|comment|advertisement|ad-|ads-|social|share|related|recommend|widget|popup|modal|banner|cookie|newsletter)",
            Pattern.CASE_INSENSITIVE
    );

    // 正文字段候选
    private static final Pattern CONTENT_PATTERNS = Pattern.compile(
            "(article|post|content|entry|main|body|text|story|blog)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 网页抓取结果
     */
    public record ScrapeResult(
            String url,
            String title,
            String author,
            String date,
            String description,
            String content,       // Markdown 正文
            String contentHtml,   // 原始 HTML 正文
            Map<String, String> metadata,
            String fileHash       // SHA256 of content (for dedup)
    ) {}

    /**
     * 抓取网页并提取正文
     */
    public ScrapeResult scrape(String url) throws Exception {
        log.info("Scraping URL: {}", url);
        
        // 1. 下载 HTML
        String html = downloadHtml(url);
        
        // 2. 用 Jsoup 解析
        Document doc = Jsoup.parse(html, url);
        
        // 3. 提取元数据
        String title = extractTitle(doc);
        String author = extractMeta(doc, "author");
        String date = extractDate(doc);
        String description = extractMeta(doc, "description");
        
        // 4. 提取正文
        Element mainContent = extractMainContent(doc);
        String contentHtml = mainContent != null ? mainContent.html() : "";
        
        // 5. 转换为 Markdown（保留关键结构）
        String markdown = htmlToMarkdown(mainContent != null ? mainContent : doc.body());
        
        // 6. 生成缓存哈希
        String fileHash = sha256(contentHtml);
        
        // 7. 收集元数据
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("url", url);
        metadata.put("title", title);
        metadata.put("author", author);
        metadata.put("date", date);
        metadata.put("description", description);
        
        log.info("Scraped '{}': {} chars of content", title, markdown.length());
        
        return new ScrapeResult(url, title, author, date, description, markdown, contentHtml, metadata, fileHash);
    }

    /**
     * 下载 HTML 内容
     */
    private String downloadHtml(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; IntelliSenseBot/1.0; +https://intelligence-platform)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + " when fetching " + url);
        }
        
        return response.body();
    }

    /**
     * 提取标题
     */
    private String extractTitle(Document doc) {
        // 优先级: og:title > h1 > title tag
        String ogTitle = extractMeta(doc, "og:title");
        if (ogTitle != null && !ogTitle.isBlank()) return ogTitle.trim();
        
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) return h1.text().trim();
        
        return doc.title();
    }

    /**
     * 提取 meta 标签内容
     */
    private String extractMeta(Document doc, String name) {
        // og:author, article:author
        Element meta = doc.selectFirst("meta[property=og:" + name + "]");
        if (meta == null) meta = doc.selectFirst("meta[property=article:" + name + "]");
        if (meta == null) meta = doc.selectFirst("meta[name=" + name + "]");
        if (meta == null) meta = doc.selectFirst("meta[itemprop=" + name + "]");
        
        if (meta != null) {
            String content = meta.attr("content");
            if (content != null && !content.isBlank()) return content.trim();
        }
        return "";
    }

    /**
     * 提取发布日期
     */
    private String extractDate(Document doc) {
        // og:article:published_time > article:published_time > date meta > time element
        String date = extractMeta(doc, "article:published_time");
        if (date == null || date.isBlank()) date = extractMeta(doc, "og:article:published_time");
        if (date == null || date.isBlank()) {
            Element timeEl = doc.selectFirst("time[datetime]");
            if (timeEl != null) date = timeEl.attr("datetime");
        }
        if (date == null || date.isBlank()) date = extractMeta(doc, "date");
        return date != null ? date.trim() : "";
    }

    /**
     * Readability-style 正文提取
     * 通过评分算法找到最可能是正文的 DOM 元素
     */
    private Element extractMainContent(Document doc) {
        Element body = doc.body();
        if (body == null) return null;
        
        // 1. 移除明显的非正文元素
        for (String tag : NON_CONTENT_TAGS) {
            doc.select(tag).remove();
        }
        
        // 2. 移除包含非正文类名的元素
        for (Element el : doc.select("[class]")) {
            String className = el.attr("class");
            if (NON_CONTENT_CLASSES.matcher(className).find()) {
                el.remove();
            }
        }
        
        // 3. 查找最可能是正文的容器
        // 优先级: <article> > <main> > [class*=article/post/content] > <body>
        Element article = doc.selectFirst("article");
        if (article != null && article.text().length() > 200) return article;
        
        Element main = doc.selectFirst("main");
        if (main != null && main.text().length() > 200) return main;
        
        // 查找类名包含 content/article/post 的元素
        for (Element el : doc.select("[class]")) {
            String className = el.attr("class");
            if (CONTENT_PATTERNS.matcher(className).find()) {
                String text = el.text();
                if (text.length() > 300) return el;
            }
        }
        
        // 回退：返回 body
        return body;
    }

    /**
     * HTML 转 Markdown（简化转换，保留标题、链接、列表、粗体/斜体、代码）
     */
    private String htmlToMarkdown(Element root) {
        StringBuilder md = new StringBuilder();
        convertNode(root, md, 0);
        return md.toString().trim();
    }

    private void convertNode(Element node, StringBuilder md, int depth) {
        if (node == null) return;
        
        String tag = node.tagName().toLowerCase();
        
        // 跳过不应该出现的元素
        if (NON_CONTENT_TAGS.contains(tag)) return;
        
        switch (tag) {
            case "h1" -> { md.append("\n\n# ").append(node.text()).append("\n\n"); return; }
            case "h2" -> { md.append("\n\n## ").append(node.text()).append("\n\n"); return; }
            case "h3" -> { md.append("\n\n### ").append(node.text()).append("\n\n"); return; }
            case "h4" -> { md.append("\n\n#### ").append(node.text()).append("\n\n"); return; }
            case "h5" -> { md.append("\n\n##### ").append(node.text()).append("\n\n"); return; }
            case "h6" -> { md.append("\n\n###### ").append(node.text()).append("\n\n"); return; }
            case "p" -> {
                md.append("\n\n");
                for (Element child : node.children()) convertInline(child, md);
                if (node.ownText() != null && !node.ownText().isBlank()) {
                    md.append(node.ownText().trim());
                }
                md.append("\n\n");
                return;
            }
            case "br" -> { md.append("\n"); return; }
            case "hr" -> { md.append("\n\n---\n\n"); return; }
            case "blockquote" -> {
                md.append("\n\n> ").append(node.text().replace("\n", "\n> ")).append("\n\n");
                return;
            }
            case "pre" -> {
                Element code = node.selectFirst("code");
                String lang = code != null ? code.attr("class").replace("language-", "") : "";
                String codeText = code != null ? code.text() : node.text();
                md.append("\n\n```").append(lang).append("\n").append(codeText).append("\n```\n\n");
                return;
            }
            case "ul" -> {
                md.append("\n");
                for (Element li : node.children()) {
                    if (li.tagName().equalsIgnoreCase("li")) {
                        md.append("- ").append(li.text()).append("\n");
                    }
                }
                md.append("\n");
                return;
            }
            case "ol" -> {
                md.append("\n");
                int idx = 1;
                for (Element li : node.children()) {
                    if (li.tagName().equalsIgnoreCase("li")) {
                        md.append(idx++).append(". ").append(li.text()).append("\n");
                    }
                }
                md.append("\n");
                return;
            }
            case "table" -> {
                md.append("\n\n").append(convertTable(node)).append("\n\n");
                return;
            }
            case "img" -> {
                String src = node.attr("src");
                String alt = node.attr("alt");
                if (src != null && !src.isBlank()) {
                    md.append("\n\n![").append(alt != null ? alt : "").append("](").append(src).append(")\n\n");
                }
                return;
            }
            case "a" -> {
                String href = node.attr("href");
                if (href != null && !href.isBlank()) {
                    md.append("[").append(node.text()).append("](").append(href).append(")");
                } else {
                    md.append(node.text());
                }
                return;
            }
            case "strong", "b" -> { md.append("**").append(node.text()).append("**"); return; }
            case "em", "i" -> { md.append("*").append(node.text()).append("*"); return; }
            case "code" -> { md.append("`").append(node.text()).append("`"); return; }
            default -> {
                // 递归处理子节点
                for (Element child : node.children()) {
                    convertNode(child, md, depth + 1);
                }
                // 处理直接文本节点
                if (node.ownText() != null && !node.ownText().isBlank() && node.children().isEmpty()) {
                    md.append(node.ownText().trim()).append("\n\n");
                }
            }
        }
    }

    private void convertInline(Element node, StringBuilder md) {
        if (node == null) return;
        String tag = node.tagName().toLowerCase();
        switch (tag) {
            case "a" -> {
                String href = node.attr("href");
                if (href != null && !href.isBlank()) {
                    md.append("[").append(node.text()).append("](").append(href).append(")");
                } else {
                    md.append(node.text());
                }
            }
            case "strong", "b" -> md.append("**").append(node.text()).append("**");
            case "em", "i" -> md.append("*").append(node.text()).append("*");
            case "code" -> md.append("`").append(node.text()).append("`");
            default -> md.append(node.text());
        }
    }

    /**
     * 简单表格转 Markdown
     */
    private String convertTable(Element table) {
        StringBuilder md = new StringBuilder();
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return "";
        
        // 表头
        Elements ths = rows.get(0).select("th, td");
        if (!ths.isEmpty()) {
            md.append("| ");
            for (Element th : ths) md.append(th.text()).append(" | ");
            md.append("\n|");
            for (int i = 0; i < ths.size(); i++) md.append(" --- |");
            md.append("\n");
        }
        
        int startIdx = ths.isEmpty() ? 0 : 1;
        for (int i = startIdx; i < rows.size(); i++) {
            Elements tds = rows.get(i).select("td, th");
            if (tds.isEmpty()) continue;
            md.append("| ");
            for (Element td : tds) md.append(td.text()).append(" | ");
            md.append("\n");
        }
        
        return md.toString();
    }

    /**
     * SHA256 哈希（用于缓存去重）
     */
    private String sha256(String input) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hash);
    }
}
