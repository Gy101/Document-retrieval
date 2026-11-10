package com.doccheck.check;

import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.parser.model.ParagraphMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按“章节 + 标题/正文角色”对齐模板与目标文档段落。
 *
 * <p>模板只是格式示例，目标文档内容长度、示例小节数量都可能变化，因此不能按全文
 * 段落序号硬配。例如模板第 37 段可能是摘要正文，目标第 37 段却可能是 ABSTRACT
 * 标题，直接比较会把“正文未加粗”误判成“标题未加粗”。
 *
 * <p>本对齐器只做保守配对：
 * <ul>
 *   <li>章节标题先精确匹配，找不到就跳过该章节；</li>
 *   <li>标题段按规范化标题文本匹配，不按序号；</li>
 *   <li>正文段只在同名章节内按非空正文序号配对；</li>
 *   <li>模板多出的示例小节、目标新增小节不强行配对。</li>
 * </ul>
 * 对齐不足时宁可少报，也不跨章节制造误报。
 */
public final class ParagraphAligner {

    private ParagraphAligner() {
    }

    /** 一对语义上对应的模板/目标段落。 */
    public record Pair(ParagraphMeta template, ParagraphMeta target) {
    }

    public static List<Pair> align(DocumentMeta templateMeta, DocumentMeta targetMeta) {
        List<ParagraphMeta> source = MetaIndex.paragraphs(templateMeta);
        List<ParagraphMeta> target = MetaIndex.paragraphs(targetMeta);
        if (source.isEmpty() || target.isEmpty()) {
            return List.of();
        }

        List<DocumentSections.DocSection> sourceSections = DocumentSections.of(templateMeta);
        List<DocumentSections.DocSection> targetSections = DocumentSections.of(targetMeta);

        // 没有可识别标题时退回“非空段落顺序配对”，保持对无标题短文档的兼容。
        if (isFlat(sourceSections) && isFlat(targetSections)) {
            return alignFlat(source, target);
        }

        List<Pair> result = new ArrayList<>();
        Set<Integer> usedTargets = new HashSet<>();

        for (ParagraphMeta templateParagraph : source) {
            if (!isAlignable(templateParagraph)) {
                continue;
            }
            DocumentSections.DocSection sourceSection =
                    DocumentSections.containing(sourceSections, templateParagraph.getIndex());
            if (sourceSection == null) {
                continue;
            }
            DocumentSections.DocSection targetSection =
                    DocumentSections.match(targetSections, sourceSection.title());
            if (targetSection == null) {
                continue;
            }

            List<ParagraphMeta> targetParagraphs = paragraphsIn(target, targetSection);
            ParagraphMeta matched = DocumentSections.isHeading(templateParagraph)
                    ? matchHeading(templateParagraph, targetParagraphs)
                    : matchBody(templateParagraph, paragraphsIn(source, sourceSection), targetParagraphs);

            if (matched == null || !isAlignable(matched) || usedTargets.contains(matched.getIndex())) {
                continue;
            }
            usedTargets.add(matched.getIndex());
            result.add(new Pair(templateParagraph, matched));
        }
        return result;
    }

    private static List<Pair> alignFlat(List<ParagraphMeta> source, List<ParagraphMeta> target) {
        List<ParagraphMeta> sourceParagraphs = source.stream().filter(ParagraphAligner::isAlignable).toList();
        List<ParagraphMeta> targetParagraphs = target.stream().filter(ParagraphAligner::isAlignable).toList();
        List<Pair> result = new ArrayList<>();
        int limit = Math.min(sourceParagraphs.size(), targetParagraphs.size());
        for (int i = 0; i < limit; i++) {
            result.add(new Pair(sourceParagraphs.get(i), targetParagraphs.get(i)));
        }
        return result;
    }

    private static ParagraphMeta matchHeading(ParagraphMeta templateParagraph,
                                               List<ParagraphMeta> targetParagraphs) {
        String wanted = DocumentSections.normalizeTitle(templateParagraph.getText());
        if (wanted.isBlank()) {
            return null;
        }
        for (ParagraphMeta targetParagraph : targetParagraphs) {
            if (!isAlignable(targetParagraph) || !DocumentSections.isHeading(targetParagraph)) {
                continue;
            }
            if (wanted.equals(DocumentSections.normalizeTitle(targetParagraph.getText()))) {
                return targetParagraph;
            }
        }
        return null;
    }

    private static ParagraphMeta matchBody(ParagraphMeta templateParagraph,
                                            List<ParagraphMeta> sourceSectionParagraphs,
                                            List<ParagraphMeta> targetSectionParagraphs) {
        if (isKeywordLine(templateParagraph)) {
            return matchKeywordLine(templateParagraph, sourceSectionParagraphs, targetSectionParagraphs);
        }

        int ordinal = 0;
        for (ParagraphMeta paragraph : sourceSectionParagraphs) {
            if (paragraph.getIndex() == templateParagraph.getIndex()) {
                break;
            }
            if (isAlignable(paragraph) && !DocumentSections.isHeading(paragraph)
                    && !isKeywordLine(paragraph)) {
                ordinal++;
            }
        }

        List<ParagraphMeta> targetBodies = targetSectionParagraphs.stream()
                .filter(p -> isAlignable(p) && !DocumentSections.isHeading(p) && !isKeywordLine(p))
                .toList();
        return ordinal < targetBodies.size() ? targetBodies.get(ordinal) : null;
    }

    /** 关键词行单独配对，避免模板“Key words”被当作正文去匹配目标摘要正文。 */
    private static ParagraphMeta matchKeywordLine(ParagraphMeta templateParagraph,
                                                   List<ParagraphMeta> sourceSectionParagraphs,
                                                   List<ParagraphMeta> targetSectionParagraphs) {
        int ordinal = 0;
        for (ParagraphMeta paragraph : sourceSectionParagraphs) {
            if (paragraph.getIndex() == templateParagraph.getIndex()) {
                break;
            }
            if (isAlignable(paragraph) && isKeywordLine(paragraph)) {
                ordinal++;
            }
        }
        List<ParagraphMeta> targetKeywordLines = targetSectionParagraphs.stream()
                .filter(p -> isAlignable(p) && isKeywordLine(p))
                .toList();
        return ordinal < targetKeywordLines.size() ? targetKeywordLines.get(ordinal) : null;
    }

    private static boolean isKeywordLine(ParagraphMeta paragraph) {
        if (isBlank(paragraph)) {
            return false;
        }
        String text = paragraph.getText().replace('\u00a0', ' ').trim().toLowerCase();
        return text.startsWith("关键词") || text.startsWith("keywords") || text.startsWith("key words");
    }

    private static List<ParagraphMeta> paragraphsIn(List<ParagraphMeta> paragraphs,
                                                     DocumentSections.DocSection section) {
        List<ParagraphMeta> result = new ArrayList<>();
        for (ParagraphMeta paragraph : paragraphs) {
            Integer index = paragraph.getIndex();
            if (index != null && index >= section.startIndex() && index <= section.endIndex()) {
                result.add(paragraph);
            }
        }
        return result;
    }

    private static boolean isFlat(List<DocumentSections.DocSection> sections) {
        return sections.isEmpty()
                || (sections.size() == 1 && "全文".equals(sections.get(0).normalized()));
    }

    private static boolean isBlank(ParagraphMeta paragraph) {
        return paragraph == null || paragraph.getText() == null || paragraph.getText().isBlank();
    }

    /** 目录行（制表符 + 页码）是自动生成内容，不作为正文格式配对目标。 */
    private static boolean isAlignable(ParagraphMeta paragraph) {
        return !isBlank(paragraph) && !paragraph.getText().contains("\t");
    }
}
