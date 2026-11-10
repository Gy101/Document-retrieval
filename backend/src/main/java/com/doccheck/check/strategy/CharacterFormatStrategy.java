package com.doccheck.check.strategy;

import com.doccheck.check.CheckContext;
import com.doccheck.check.DiffSupport;
import com.doccheck.check.FormatCheckStrategy;
import com.doccheck.check.MetaIndex;
import com.doccheck.check.ParagraphAligner;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.DocumentMeta;
import com.doccheck.parser.model.ParagraphMeta;
import com.doccheck.parser.model.RunMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 字符级格式策略。核心的全量逐 Run 比对交给 DSH，本地只做「模板统一、目标混排」的确定性提示，
 * 避免与模型结果大量重复。
 */
@Service
public class CharacterFormatStrategy implements FormatCheckStrategy {

    @Override
    public String name() {
        return "character-format";
    }

    @Override
    public String dimension() {
        return "字符级";
    }

    @Override
    public String promptGuidance() {
        return "逐 Run 比对中英文字体、字号、字重、斜体、下划线、颜色、字符间距、全角半角与中英文标点混用；"
                + "模板同一段落内字体统一而目标混排时，必须逐字符标出异常片段。"
                + "中文引号“”‘’使用哪套字体由 w:rFonts/@w:hint 决定：hint=eastAsia 用中文字体，"
                + "default/ascii 用西文字体；引号显示为 Times New Roman 而模板为宋体时必须标出。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<FormatDiff> result = new ArrayList<>();
        List<ParagraphAligner.Pair> pairs =
                ParagraphAligner.align(context.getTemplateMeta(), context.getTargetMeta());
        int limit = Math.min(80, pairs.size());
        for (int i = 0; i < limit; i++) {
            ParagraphMeta tp = pairs.get(i).template();
            ParagraphMeta dp = pairs.get(i).target();
            if (dp.getText() == null || dp.getText().isBlank()) {
                continue;
            }
            if (!tp.isMixedFont() && dp.isMixedFont()) {
                result.add(DiffSupport.of("字体", Severity.MIDDLE, "text",
                        DiffSupport.anchorOfParagraph(dp.getIndex()), "第 " + (dp.getIndex() + 1) + " 段",
                        "字体统一", "同段内中英文字体混排", dp.getText(),
                        "统一该段中英文字体，避免宋体/仿宋混用"));
            }
            if (!tp.isMixedFontSize() && dp.isMixedFontSize()) {
                result.add(DiffSupport.of("字号", Severity.MIDDLE, "text",
                        DiffSupport.anchorOfParagraph(dp.getIndex()), "第 " + (dp.getIndex() + 1) + " 段",
                        "字号统一", "同段内字号不一致", dp.getText(),
                        "将该段 Run 的字号统一为模板字号"));
            }
        }

        appendQuoteFontDiffs(context, result);
        return result;
    }

    /**
     * 中文引号是“歧义字符”：Word/WPS 在 w:hint=eastAsia 时使用中文字体，
     * 在 default/ascii 时使用西文字体。模板与目标常常都写着宋体/Times New Roman，
     * 但引号实际落入不同字体，普通的中文字体比较无法发现。
     */
    private void appendQuoteFontDiffs(CheckContext context, List<FormatDiff> result) {
        String expected = expectedQuoteFont(context.getTemplateMeta());
        if (expected == null || expected.isBlank()) {
            return;
        }
        int added = 0;
        for (ParagraphMeta paragraph : MetaIndex.paragraphs(context.getTargetMeta())) {
            if (paragraph.getText() == null || !containsQuote(paragraph.getText())) {
                continue;
            }
            for (RunMeta run : paragraph.getRuns() == null ? List.<RunMeta>of() : paragraph.getRuns()) {
                if (run.getText() == null || !containsQuote(run.getText())) {
                    continue;
                }
                String actual = displayedQuoteFont(run);
                if (actual != null && !actual.isBlank() && !actual.equalsIgnoreCase(expected)) {
                    result.add(DiffSupport.of("引号字体", Severity.MIDDLE, "text",
                            DiffSupport.anchorOfParagraph(paragraph.getIndex()),
                            "第 " + (paragraph.getIndex() + 1) + " 段",
                            expected, actual, paragraph.getText(),
                            "将中文引号字体调整为「" + expected + "」，并保持 w:hint=eastAsia"));
                    added++;
                    break;
                }
            }
            if (added >= 30) {
                break;
            }
        }
    }

    private String expectedQuoteFont(DocumentMeta meta) {
        if (meta == null) {
            return null;
        }
        Map<String, Integer> counts = new HashMap<>();
        String bodyFont = null;
        for (ParagraphMeta paragraph : MetaIndex.paragraphs(meta)) {
            for (RunMeta run : paragraph.getRuns() == null ? List.<RunMeta>of() : paragraph.getRuns()) {
                if (bodyFont == null && run.getFontEastAsia() != null && !run.getFontEastAsia().isBlank()) {
                    bodyFont = run.getFontEastAsia();
                }
                if (run.getText() == null || !containsQuote(run.getText())) {
                    continue;
                }
                String font = displayedQuoteFont(run);
                if (font != null && !font.isBlank()) {
                    counts.merge(font, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(bodyFont);
    }

    private String displayedQuoteFont(RunMeta run) {
        String hint = run.getFontHint() == null ? "" : run.getFontHint().toLowerCase(Locale.ROOT);
        boolean eastAsia = hint.contains("east");
        String font = eastAsia ? run.getFontEastAsia() : run.getFontAscii();
        if (font == null || font.isBlank()) {
            font = run.getFontEastAsia();
        }
        return font;
    }

    private boolean containsQuote(String text) {
        return text.indexOf('“') >= 0 || text.indexOf('”') >= 0
                || text.indexOf('‘') >= 0 || text.indexOf('’') >= 0;
    }
}
