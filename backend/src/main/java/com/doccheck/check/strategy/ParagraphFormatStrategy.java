package com.doccheck.check.strategy;

import com.doccheck.check.*;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.IndentMeta;
import com.doccheck.parser.model.ParagraphMeta;
import com.doccheck.parser.model.ParagraphSpacingMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 段落级格式策略：对齐、缩进、段前段后、行距、大纲级别、边框底纹。 */
@Service
public class ParagraphFormatStrategy implements FormatCheckStrategy {

    private static final double PT_TOLERANCE = 1.0;
    private static final double LINE_TOLERANCE = 0.1;

    @Override
    public String name() {
        return "paragraph-format";
    }

    @Override
    public String dimension() {
        return "段落级";
    }

    @Override
    public String promptGuidance() {
        return "比对对齐方式、首行/悬挂缩进（优先字符单位）、段前段后间距、行距、大纲级别与边框底纹；"
                + "容差：字号 0.5 磅、间距 0.1 行或 1 磅、页边距 0.2 厘米。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<FormatDiff> result = new ArrayList<>();
        List<ParagraphAligner.Pair> pairs =
                ParagraphAligner.align(context.getTemplateMeta(), context.getTargetMeta());
        int limit = Math.min(120, pairs.size());
        for (int i = 0; i < limit; i++) {
            ParagraphMeta tp = pairs.get(i).template();
            ParagraphMeta dp = pairs.get(i).target();
            if ((tp.getText() == null || tp.getText().isBlank())
                    && (dp.getText() == null || dp.getText().isBlank())) {
                continue;
            }
            String anchor = DiffSupport.anchorOfParagraph(dp.getIndex());
            String location = "第 " + (dp.getIndex() + 1) + " 段";
            String snippet = dp.getText();
            if (!Objects.equals(tp.getAlignment(), dp.getAlignment())) {
                result.add(DiffSupport.of("段落", Severity.MIDDLE, "paragraph", anchor, location,
                        alignmentName(tp.getAlignment()), alignmentName(dp.getAlignment()), snippet,
                        "将该段对齐方式调整为" + alignmentName(tp.getAlignment())));
            }
            IndentMeta ti = tp.getIndentation();
            IndentMeta di = dp.getIndentation();
            if (ti != null && di != null) {
                if (ti.getFirstLineChars() != null && di.getFirstLineChars() != null) {
                    if (!Objects.equals(ti.getFirstLineChars(), di.getFirstLineChars())) {
                        result.add(DiffSupport.of("段落", Severity.MIDDLE, "paragraph", anchor, location,
                                "首行缩进 " + ti.getFirstLineChars() + " 字符",
                                "首行缩进 " + di.getFirstLineChars() + " 字符", snippet,
                                "将首行缩进调整为 " + ti.getFirstLineChars() + " 字符"));
                    }
                } else if (Math.abs(ti.getFirstLinePt() - di.getFirstLinePt()) > PT_TOLERANCE) {
                    result.add(DiffSupport.of("段落", Severity.MIDDLE, "paragraph", anchor, location,
                            "首行缩进 " + round(ti.getFirstLinePt()) + "pt",
                            "首行缩进 " + round(di.getFirstLinePt()) + "pt", snippet,
                            "将首行缩进调整为 " + round(ti.getFirstLinePt()) + "pt"));
                }
                if (Math.abs(ti.getLeftPt() - di.getLeftPt()) > PT_TOLERANCE) {
                    result.add(DiffSupport.of("段落", Severity.LOW, "paragraph", anchor, location,
                            "左缩进 " + round(ti.getLeftPt()) + "pt", "左缩进 " + round(di.getLeftPt()) + "pt",
                            snippet, "将左缩进调整为 " + round(ti.getLeftPt()) + "pt"));
                }
            }
            ParagraphSpacingMeta ts = tp.getSpacing();
            ParagraphSpacingMeta ds = dp.getSpacing();
            if (ts != null && ds != null) {
                if (ts.getLineSpacingLines() != null && ds.getLineSpacingLines() != null
                        && Math.abs(ts.getLineSpacingLines() - ds.getLineSpacingLines()) > LINE_TOLERANCE) {
                    result.add(DiffSupport.of("段落", Severity.MIDDLE, "paragraph", anchor, location,
                            "行距 " + round(ts.getLineSpacingLines()) + " 倍",
                            "行距 " + round(ds.getLineSpacingLines()) + " 倍", snippet,
                            "将行距调整为 " + round(ts.getLineSpacingLines()) + " 倍"));
                }
                if (Math.abs(ts.getBeforePt() - ds.getBeforePt()) > PT_TOLERANCE) {
                    result.add(DiffSupport.of("段落", Severity.LOW, "paragraph", anchor, location,
                            "段前 " + round(ts.getBeforePt()) + "pt", "段前 " + round(ds.getBeforePt()) + "pt",
                            snippet, "将段前间距调整为 " + round(ts.getBeforePt()) + "pt"));
                }
                if (Math.abs(ts.getAfterPt() - ds.getAfterPt()) > PT_TOLERANCE) {
                    result.add(DiffSupport.of("段落", Severity.LOW, "paragraph", anchor, location,
                            "段后 " + round(ts.getAfterPt()) + "pt", "段后 " + round(ds.getAfterPt()) + "pt",
                            snippet, "将段后间距调整为 " + round(ts.getAfterPt()) + "pt"));
                }
            }
            if (!Objects.equals(tp.getBorders(), dp.getBorders())) {
                result.add(DiffSupport.of("段落", Severity.LOW, "paragraph", anchor, location,
                        tp.getBorders() == null ? "无边框" : tp.getBorders().toString(),
                        dp.getBorders() == null ? "无边框" : dp.getBorders().toString(), snippet,
                        "按模板设置段落边框"));
            }
        }
        return result;
    }

    private String alignmentName(String value) {
        if (value == null) {
            return "未设置";
        }
        return switch (value.toLowerCase()) {
            case "left" -> "左对齐";
            case "center" -> "居中";
            case "right" -> "右对齐";
            case "both" -> "两端对齐";
            case "distribute" -> "分散对齐";
            default -> value;
        };
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
