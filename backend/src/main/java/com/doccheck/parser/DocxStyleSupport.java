package com.doccheck.parser;

import com.doccheck.parser.model.DocumentStylesMeta;
import com.doccheck.parser.model.StyleMeta;
import org.apache.poi.xwpf.usermodel.XWPFDefaultRunStyle;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.doccheck.parser.DocxFormatSupport.asDouble;
import static com.doccheck.parser.DocxFormatSupport.first;
import static com.doccheck.parser.DocxFormatSupport.halfPointsToPt;
import static com.doccheck.parser.DocxFormatSupport.twipsToPt;

/** 收集文档实际使用到的样式并转换为展示用元数据。 */
public final class DocxStyleSupport {

    private DocxStyleSupport() {
    }

    public static DocumentStylesMeta parse(XWPFDocument doc) {
        XWPFStyles styles = doc.getStyles();
        if (styles == null) {
            return null;
        }
        String eastAsia = null;
        String ascii = null;
        Double size = null;
        // XWPFDefaultRunStyle 在 POI 5.2.5 仅暴露字号，不提供按 FontCharRange 取字体。
        if (styles.getDefaultRunStyle() != null) {
            XWPFDefaultRunStyle runStyle = styles.getDefaultRunStyle();
            size = runStyle.getFontSizeAsDouble();
        }

        // POI 5.2.x 的 XWPFStyles 不暴露「全部样式列表」，这里从正文/表格/页眉页脚实际
        // 使用到的 styleId 反查 XWPFStyle，既能覆盖真实格式，也避免反射内部结构。
        Set<String> styleIds = new LinkedHashSet<>();
        collectParagraphStyleIds(doc.getParagraphs(), styleIds);
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    collectParagraphStyleIds(cell.getParagraphs(), styleIds);
                }
            }
        }
        for (XWPFHeaderFooter hf : doc.getHeaderList()) {
            collectParagraphStyleIds(hf.getParagraphs(), styleIds);
        }
        for (XWPFHeaderFooter hf : doc.getFooterList()) {
            collectParagraphStyleIds(hf.getParagraphs(), styleIds);
        }
        if (styleIds.isEmpty()) {
            styleIds.add("Normal");
        }

        List<StyleMeta> paragraphStyles = new ArrayList<>();
        for (String styleId : styleIds) {
            XWPFStyle style = styles.getStyle(styleId);
            if (style == null) {
                continue;
            }
            try {
                CTStyle ct = style.getCTStyle();
                String type = ct.getType() == null ? "" : ct.getType().toString().toLowerCase(Locale.ROOT);
                Double styleSize = null;
                String styleEastAsia = null;
                String styleAscii = null;
                Boolean bold = null;
                String alignment = null;
                Double firstLine = null;
                Double lineSpacing = null;
                CTRPr rPr = ct.getRPr();
                if (rPr != null) {
                    CTFonts fonts = first(rPr.getRFontsList());
                    if (fonts != null) {
                        styleEastAsia = fonts.getEastAsia();
                        styleAscii = fonts.getAscii();
                    }
                    CTHpsMeasure sz = first(rPr.getSzList());
                    if (sz != null) {
                        styleSize = halfPointsToPt(sz.getVal());
                    }
                    CTOnOff b = first(rPr.getBList());
                    if (b != null) {
                        Object value = b.getVal();
                        bold = value == null || Boolean.parseBoolean(value.toString());
                    }
                }
                if (ct.getPPr() != null) {
                    if (ct.getPPr().getJc() != null && ct.getPPr().getJc().getVal() != null) {
                        alignment = ct.getPPr().getJc().getVal().toString().toLowerCase(Locale.ROOT);
                    }
                    if (ct.getPPr().getInd() != null && ct.getPPr().getInd().getFirstLine() != null) {
                        firstLine = twipsToPt(ct.getPPr().getInd().getFirstLine());
                    }
                    if (ct.getPPr().getSpacing() != null && ct.getPPr().getSpacing().getLine() != null) {
                        lineSpacing = asDouble(ct.getPPr().getSpacing().getLine()) / 240.0;
                    }
                }
                if (!"paragraph".equals(type) && !"character".equals(type) && !"table".equals(type)) {
                    continue;
                }
                paragraphStyles.add(StyleMeta.builder()
                        .id(style.getStyleId())
                        .name(style.getName())
                        .type(type)
                        .basedOn(style.getBasisStyleID())
                        .fontEastAsia(styleEastAsia)
                        .fontAscii(styleAscii)
                        .fontSizePt(styleSize)
                        .bold(bold)
                        .alignment(alignment)
                        .firstLinePt(firstLine)
                        .lineSpacingLines(lineSpacing)
                        .build());
            } catch (RuntimeException ignored) {
                // 单条样式异常不影响文档整体解析
            }
        }
        return DocumentStylesMeta.builder()
                .defaultFontEastAsia(eastAsia)
                .defaultFontAscii(ascii)
                .defaultFontSizePt(size)
                .paragraphStyles(paragraphStyles)
                .build();
    }

    private static void collectParagraphStyleIds(List<XWPFParagraph> paragraphs, Set<String> target) {
        if (paragraphs == null) {
            return;
        }
        for (XWPFParagraph paragraph : paragraphs) {
            if (paragraph.getStyleID() != null && !paragraph.getStyleID().isBlank()) {
                target.add(paragraph.getStyleID());
            }
        }
    }
}
