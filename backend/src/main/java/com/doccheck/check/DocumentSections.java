package com.doccheck.check;

import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.parser.model.ParagraphMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 按标题把文档切分为章节，用于「模板摘要 ↔ 文档摘要」这类内容级对齐。
 *
 * <p>POI 不会自动给出章节树，且很多学校模板的标题样式为空；这里组合大纲级别、
 * 样式名、常见中文章节编号和固定标题词做启发式识别。章节范围包含标题段，
 * 但不包含下一个标题段，字符数统计时忽略空白。
 */
public final class DocumentSections {

    private static final Set<String> KNOWN_TITLES = Set.of(
            "摘要", "abstract", "目录", "引言", "绪论", "结论", "参考文献", "致谢", "附录",
            "研究背景", "研究意义", "关键词");

    private DocumentSections() {
    }

    public record DocSection(String title,
                             String normalized,
                             int startIndex,
                             int endIndex,
                             int characterCount,
                             String text) {
    }

    public static List<DocSection> of(DocumentMeta meta) {
        List<ParagraphMeta> paragraphs = MetaIndex.paragraphs(meta);
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<Integer> headingPositions = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (isHeading(paragraphs.get(i))) {
                headingPositions.add(i);
            }
        }
        if (headingPositions.isEmpty()) {
            return List.of(build(paragraphs, 0, paragraphs.size() - 1, "全文"));
        }

        List<DocSection> result = new ArrayList<>();
        // 封面、声明等第一个标题之前的内容单独成节，避免被并入“摘要”
        if (headingPositions.get(0) > 0) {
            result.add(build(paragraphs, 0, headingPositions.get(0) - 1, "前言"));
        }
        for (int i = 0; i < headingPositions.size(); i++) {
            int start = headingPositions.get(i);
            int end = i + 1 < headingPositions.size()
                    ? headingPositions.get(i + 1) - 1
                    : paragraphs.size() - 1;
            result.add(build(paragraphs, start, end, paragraphs.get(start).getText()));
        }
        return result;
    }

    /** 找到包含指定全局段落序号的章节。 */
    public static DocSection containing(List<DocSection> sections, Integer paragraphIndex) {
        if (paragraphIndex == null) {
            return null;
        }
        for (DocSection section : sections) {
            if (paragraphIndex >= section.startIndex() && paragraphIndex <= section.endIndex()) {
                return section;
            }
        }
        return null;
    }

    /**
     * 在目标文档中匹配模板章节。优先完整标题，其次包含关系；
     * 例如“摘要 / 摘 要 / 中文摘要”都能命中。
     */
    public static DocSection match(List<DocSection> targetSections, String templateTitle) {
        String wanted = normalizeTitle(templateTitle);
        if (wanted.isBlank()) {
            return null;
        }
        for (DocSection section : targetSections) {
            if (wanted.equals(section.normalized())) {
                return section;
            }
        }
        for (DocSection section : targetSections) {
            String actual = section.normalized();
            if (actual.isBlank()) {
                continue;
            }
            if (actual.contains(wanted) || wanted.contains(actual)) {
                return section;
            }
        }
        return null;
    }

    public static String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        String value = title.replace('\u00a0', ' ').replace('\u3000', ' ').trim();
        value = value.replaceAll("\\s+", "");
        value = value.replaceFirst("^第[一二三四五六七八九十百千0-9]+[章节篇部分]+", "");
        value = value.replaceFirst("^\\d+(?:\\.\\d+){0,3}[、.．]?", "");
        value = value.replaceAll("^[、.．:：\\-—]+", "");
        value = value.toLowerCase(Locale.ROOT);
        return value.replaceAll("[\\s\\u3000]", "");
    }

    private static DocSection build(List<ParagraphMeta> paragraphs, int start, int end, String fallbackTitle) {
        StringBuilder text = new StringBuilder();
        for (int i = start; i <= end && i < paragraphs.size(); i++) {
            String line = paragraphs.get(i).getText();
            if (line != null && !line.isBlank()) {
                text.append(line).append('\n');
            }
        }
        String rawTitle = fallbackTitle == null ? "" : fallbackTitle.trim().replaceAll("\\s+", " ");
        return new DocSection(rawTitle,
                normalizeTitle(rawTitle),
                paragraphs.get(start).getIndex(),
                paragraphs.get(end).getIndex(),
                text.toString().replaceAll("[\\s\\u3000]", "").length(),
                text.toString());
    }

    /** 供段落对齐器判断“标题 / 正文”角色。 */
    public static boolean isHeading(ParagraphMeta paragraph) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.replace('\u00a0', ' ').replace('\u3000', ' ').trim();
        if (trimmed.length() > 80 || trimmed.contains("\t")) {
            return false;
        }
        if (paragraph.getOutlineLevel() != null) {
            return true;
        }
        String style = paragraph.getStyleName() != null
                ? paragraph.getStyleName() : paragraph.getStyleId();
        if (style != null) {
            String lower = style.toLowerCase(Locale.ROOT);
            if (lower.contains("heading") || style.contains("标题")) {
                return true;
            }
        }
        String compact = trimmed.replaceAll("\\s+", "");
        if (compact.matches("^第[一二三四五六七八九十百千0-9]+[章节篇部分].*")) {
            return true;
        }
        if (compact.matches("^\\d+(?:\\.\\d+){1,3}[、.．]?[^\\d].*")) {
            return true;
        }
        return KNOWN_TITLES.contains(normalizeTitle(trimmed));
    }
}
