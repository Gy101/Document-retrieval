package com.doccheck.parser;

import com.doccheck.parser.model.RunMeta;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHighlight;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSignedHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSignedTwipsMeasure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.doccheck.parser.DocxFormatSupport.first;
import static com.doccheck.parser.DocxFormatSupport.halfPointsToPt;
import static com.doccheck.parser.DocxFormatSupport.twipsToPt;

/** 解析 Run 级字符格式，包括样式继承后的字体与 w:hint。 */
public final class DocxRunParser {

    private DocxRunParser() {
    }

    private record RunFonts(String eastAsia, String ascii, String hAnsi, String hint) {
    }

    public static RunMeta parse(XWPFParagraph p, XWPFRun run, int index) {
        CTRPr rPr = run.getCTR().getRPr();
        Double fontSize = null;

        // 字体同样需要沿“直接格式 → 字符样式 → 段落样式 → Normal”继承，
        // 否则大量只依赖默认正文样式的 Run 会得到 null，无法判断引号实际使用哪套字体。
        RunFonts fonts = effectiveRunFonts(p, run);
        try {
            fontSize = run.getFontSizeAsDouble();
        } catch (RuntimeException ignored) {
            // 某些文档的 w:sz 非法时 POI 会抛异常，保留 null 交给模型判断
        }

        Double characterSpacing = null;
        Double kerning = null;
        Double position = null;
        String highlight = null;
        if (rPr != null) {
            // POI 5.2.x 的 CTRPr 对可重复属性只暴露 List / Array 形式，取第一个即可。
            CTSignedTwipsMeasure spacing = first(rPr.getSpacingList());
            if (spacing != null) {
                characterSpacing = twipsToPt(spacing.getVal());
            }
            CTHpsMeasure kern = first(rPr.getKernList());
            if (kern != null) {
                kerning = twipsToPt(kern.getVal());
            }
            CTSignedHpsMeasure pos = first(rPr.getPositionList());
            if (pos != null) {
                // w:position 的单位是 half-point
                position = halfPointsToPt(pos.getVal());
            }
            CTHighlight hl = first(rPr.getHighlightList());
            if (hl != null && hl.getVal() != null) {
                highlight = hl.getVal().toString().toLowerCase(Locale.ROOT);
            }
        }

        return RunMeta.builder()
                .index(index)
                .text(run.text())
                .styleId(run.getStyle())
                .fontEastAsia(fonts.eastAsia())
                .fontAscii(fonts.ascii())
                .fontHAnsi(fonts.hAnsi())
                .fontHint(fonts.hint())
                .fontSizePt(fontSize)
                .bold(run.isBold())
                .italic(run.isItalic())
                .underline(run.getUnderline() == null ? "none" : run.getUnderline().toString().toLowerCase(Locale.ROOT))
                .strike(run.isStrikeThrough())
                .color(run.getColor())
                .highlight(highlight)
                .characterSpacingPt(characterSpacing)
                .kerningPt(kerning)
                .positionPt(position)
                .smallCaps(run.isSmallCaps())
                .allCaps(run.isCapitalized())
                .hidden(run.isVanish())
                .verticalAlign(run.getVerticalAlignment() == null ? null
                        : run.getVerticalAlignment().toString().toLowerCase(Locale.ROOT))
                .build();
    }

    private static RunFonts effectiveRunFonts(XWPFParagraph p, XWPFRun run) {
        List<CTRPr> chain = new ArrayList<>();
        if (run.getCTR() != null && run.getCTR().getRPr() != null) {
            chain.add(run.getCTR().getRPr());
        }
        XWPFDocument doc = p.getDocument();
        XWPFStyles styles = doc == null ? null : doc.getStyles();
        Set<String> seen = new LinkedHashSet<>();
        if (styles != null) {
            addStyleRunProperties(styles, run.getStyle(), chain, seen);
            String paragraphStyle = p.getStyleID() != null ? p.getStyleID() : p.getStyle();
            addStyleRunProperties(styles, paragraphStyle, chain, seen);

            XWPFStyle normal = styles.getStyle("Normal");
            if (normal == null) {
                normal = styles.getStyleWithName("Normal");
            }
            addStyleRunProperties(styles, normal, chain, seen);
        }

        String eastAsia = null;
        String ascii = null;
        String hAnsi = null;
        String hint = null;
        for (CTRPr rPr : chain) {
            CTFonts fonts = first(rPr.getRFontsList());
            if (fonts == null) {
                continue;
            }
            if (eastAsia == null) {
                eastAsia = fonts.getEastAsia();
            }
            if (ascii == null) {
                ascii = fonts.getAscii();
            }
            if (hAnsi == null) {
                hAnsi = fonts.getHAnsi();
            }
            if (hint == null && fonts.getHint() != null) {
                hint = fonts.getHint().toString();
            }
        }
        return new RunFonts(eastAsia, ascii, hAnsi, hint);
    }

    private static void addStyleRunProperties(XWPFStyles styles, String styleId,
                                       List<CTRPr> chain, Set<String> seen) {
        addStyleRunProperties(styles, styleId == null ? null : styles.getStyle(styleId), chain, seen);
    }

    private static void addStyleRunProperties(XWPFStyles styles, XWPFStyle style,
                                       List<CTRPr> chain, Set<String> seen) {
        while (style != null && style.getStyleId() != null && seen.add(style.getStyleId())) {
            if (style.getCTStyle() != null && style.getCTStyle().getRPr() != null) {
                chain.add(style.getCTStyle().getRPr());
            }
            String basedOn = style.getBasisStyleID();
            style = basedOn == null ? null : styles.getStyle(basedOn);
        }
    }
}
