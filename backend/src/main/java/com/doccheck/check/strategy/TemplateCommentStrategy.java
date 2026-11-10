package com.doccheck.check.strategy;

import com.doccheck.check.CheckContext;
import com.doccheck.check.DiffSupport;
import com.doccheck.check.DocumentSections;
import com.doccheck.check.FormatCheckStrategy;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.CommentMeta;
import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.parser.model.ParagraphMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板批注驱动的章节级内容检查。
 *
 * <p>模板作者常把“摘要 300-600 字”“关键词 3-8 个”写在 Word 批注里。这里解析批注中的
 * 数值约束，再按章节标题把模板摘要与目标摘要对齐，避免使用全文段落序号导致封面/目录错位。
 * 更复杂的语义要求由 DSH 结合批注内容判断。
 */
@Service
public class TemplateCommentStrategy implements FormatCheckStrategy {

    private static final Pattern LENGTH_RANGE = Pattern.compile(
            "(\\d{2,5})\\s*(?:-|–|—|~|～|至|到|－)\\s*(\\d{2,5})\\s*字");

    private static final Pattern KEYWORD_RANGE = Pattern.compile(
            "关键词[^0-9]{0,12}(\\d+)\\s*(?:-|–|—|~|～|至|到|－)\\s*(\\d+)\\s*个");

    @Override
    public String name() {
        return "template-comment";
    }

    @Override
    public String dimension() {
        return "模板批注";
    }

    @Override
    public String promptGuidance() {
        return "模板批注是模板作者对该章节的明确要求，优先级高于通用格式规则；"
                + "请把批注中的字数、关键词数量、段落/字体等要求应用到目标文档的同名章节，"
                + "不要用全文段落序号机械对应。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<FormatDiff> diffs = new ArrayList<>();
        DocumentMeta template = context.getTemplateMeta();
        DocumentMeta target = context.getTargetMeta();
        if (template == null || target == null
                || template.getComments() == null || template.getComments().isEmpty()) {
            return diffs;
        }
        List<DocumentSections.DocSection> templateSections = DocumentSections.of(template);
        List<DocumentSections.DocSection> targetSections = DocumentSections.of(target);
        if (targetSections.isEmpty()) {
            return diffs;
        }

        for (CommentMeta comment : template.getComments()) {
            String text = comment.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            DocumentSections.DocSection templateSection =
                    DocumentSections.containing(templateSections, comment.getParagraphIndex());
            if (templateSection == null) {
                continue;
            }
            DocumentSections.DocSection targetSection =
                    DocumentSections.match(targetSections, templateSection.title());
            if (targetSection == null) {
                continue;
            }

            Matcher length = LENGTH_RANGE.matcher(text);
            if (length.find()) {
                int min = Integer.parseInt(length.group(1));
                int max = Integer.parseInt(length.group(2));
                if (min > max) {
                    int swap = min;
                    min = max;
                    max = swap;
                }
                if (targetSection.characterCount() < min || targetSection.characterCount() > max) {
                    diffs.add(DiffSupport.of(
                            "内容长度",
                            "middle",
                            "text",
                            DiffSupport.anchorOfParagraph(targetSection.startIndex()),
                            sectionLocation(targetSection, "正文内容"),
                            min + "-" + max + " 字",
                            targetSection.characterCount() + " 字",
                            targetSection.text(),
                            "按模板批注调整「" + targetSection.title() + "」正文篇幅至 " + min + "-" + max + " 字"));
                }
            }

            Matcher keyword = KEYWORD_RANGE.matcher(text);
            if (keyword.find()) {
                int min = Integer.parseInt(keyword.group(1));
                int max = Integer.parseInt(keyword.group(2));
                if (min > max) {
                    int swap = min;
                    min = max;
                    max = swap;
                }
                ParagraphMeta keywordParagraph = findKeywordParagraph(target);
                int count = keywordParagraph == null ? 0 : countKeywords(keywordParagraph.getText());
                if (count < min || count > max) {
                    diffs.add(DiffSupport.of(
                            "关键词数量",
                            "low",
                            "text",
                            keywordParagraph == null
                                    ? DiffSupport.anchorOfParagraph(targetSection.startIndex())
                                    : DiffSupport.anchorOfParagraph(keywordParagraph.getIndex()),
                            sectionLocation(targetSection, "关键词"),
                            min + "-" + max + " 个",
                            count + " 个",
                            keywordParagraph == null ? "" : keywordParagraph.getText(),
                            "按模板批注保留 " + min + "-" + max + " 个关键词，使用中文分号分隔"));
                }
            }
        }
        return diffs;
    }

    private String sectionLocation(DocumentSections.DocSection section, String part) {
        String title = section.title() == null || section.title().isBlank() ? "未命名章节" : section.title();
        return title + " · " + part;
    }

    private ParagraphMeta findKeywordParagraph(DocumentMeta meta) {
        for (ParagraphMeta paragraph : com.doccheck.check.MetaIndex.paragraphs(meta)) {
            String text = paragraph.getText();
            if (text == null) {
                continue;
            }
            String compact = text.replaceAll("[\\s\\u3000]", "");
            if (compact.startsWith("关键词") || compact.toLowerCase(Locale.ROOT).startsWith("keywords")) {
                return paragraph;
            }
        }
        return null;
    }

    private int countKeywords(String text) {
        if (text == null) {
            return 0;
        }
        int colon = Math.max(text.indexOf('：'), text.indexOf(':'));
        String value = colon >= 0 ? text.substring(colon + 1) : text;
        int count = 0;
        for (String part : value.split("[；;]")) {
            if (!part.replaceAll("[\\s\\u3000]", "").isBlank()) {
                count++;
            }
        }
        return count;
    }
}
