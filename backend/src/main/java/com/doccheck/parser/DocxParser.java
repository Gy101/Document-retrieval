package com.doccheck.parser;

import com.doccheck.parser.model.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTAnchor;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.doccheck.parser.DocxFormatSupport.asDouble;
import static com.doccheck.parser.DocxFormatSupport.colorHex;
import static com.doccheck.parser.DocxFormatSupport.emuToPt;
import static com.doccheck.parser.DocxFormatSupport.first;
import static com.doccheck.parser.DocxFormatSupport.halfPointsToPt;
import static com.doccheck.parser.DocxFormatSupport.hasCjkPunctuation;
import static com.doccheck.parser.DocxFormatSupport.isFullWidth;
import static com.doccheck.parser.DocxFormatSupport.twipsToPt;

import org.springframework.stereotype.Component;

/**
 * 基于 Apache POI 的 .docx 格式元数据解析器。
 *
 * <p>输出目标是「结构化、可序列化、可定位」的 {@link DocumentMeta}：
 * <ul>
 *   <li>正文按 body 顺序保留段落/表格块，段落使用全局序号 body.p[n]，表格使用 body.table[n]；</li>
 *   <li>字符级属性落在 RunMeta，段落级属性落在 ParagraphMeta，页面级属性落在 SectionMeta.pageSetup；</li>
 *   <li>页眉页脚独立解析为 header[i].p[j] / footer[i].p[j]，供前端精确定位；</li>
 *   <li>所有长度统一换算为 pt（twips / 20，EMU / 12700），避免前端再次处理单位。</li>
 * </ul>
 *
 * <p>解析器不修改文档，只读取格式；遇到不支持的内容控件会在 notes 中记录，不中断主流程。
 */
@Component
public class DocxParser {

    static {
        // 双保险：即使文件绕过上传校验（历史文件/内部调用），POI 仍限制解压膨胀。
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(10L * 1024 * 1024);
    }

    /** 图片所在容器，用于生成 header[i].p[j] / footer[i].p[j] / 表格单元格定位。 */
    private record ImageContainer(String type, Integer headerIndex, Integer footerIndex,
                                  Integer tableIndex, Integer rowIndex, Integer cellIndex) {
    }

    /** Run 的最终字体，包含决定中文标点走向的 w:hint。 */
    private record RunFonts(String eastAsia, String ascii, String hAnsi, String hint) {
    }

    /** 解析本地文件。 */
    public DocumentMeta parse(Path path, String fileName) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return parse(in, fileName);
        }
    }

    /** 解析输入流；调用方负责关闭底层文档资源。 */
    public DocumentMeta parse(InputStream inputStream, String fileName) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            return parseDocument(doc, fileName);
        }
    }

    private DocumentMeta parseDocument(XWPFDocument doc, String fileName) {
        List<SectionMeta> sections = new ArrayList<>();
        List<CTSectPr> sectionProps = new ArrayList<>();
        sections.add(SectionMeta.builder()
                .index(0)
                .blocks(new ArrayList<>())
                .breakType("nextPage")
                .build());

        List<ImageMeta> images = new ArrayList<>();
        List<CommentMeta> comments = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int paragraphIndex = 0;
        int tableIndex = 0;
        int blockIndex = 0;
        int sectionIndex = 0;

        for (IBodyElement element : doc.getBodyElements()) {
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                XWPFParagraph p = (XWPFParagraph) element;
                ParagraphMeta paragraph = parseParagraph(p, paragraphIndex, sectionIndex, images, notes);
                collectComments(p, paragraphIndex, sectionIndex, doc, comments);
                sections.get(sectionIndex).getBlocks().add(BlockMeta.builder()
                        .type("paragraph")
                        .blockIndex(blockIndex++)
                        .sectionIndex(sectionIndex)
                        .paragraph(paragraph)
                        .build());
                paragraphIndex++;

                CTSectPr sectPr = sectPrOf(p);
                if (sectPr != null) {
                    sectionProps.add(sectPr);
                    sectionIndex++;
                    sections.add(SectionMeta.builder()
                            .index(sectionIndex)
                            .blocks(new ArrayList<>())
                            .breakType(breakTypeOf(sectPr))
                            .build());
                }
            } else if (element.getElementType() == BodyElementType.TABLE) {
                XWPFTable table = (XWPFTable) element;
                TableMeta tableMeta = parseTable(table, tableIndex, paragraphIndex, sectionIndex, images, notes);
                sections.get(sectionIndex).getBlocks().add(BlockMeta.builder()
                        .type("table")
                        .blockIndex(blockIndex++)
                        .sectionIndex(sectionIndex)
                        .table(tableMeta)
                        .build());
                tableIndex++;
            }
        }

        // 文档最后的 sectPr 描述最后一节；去掉因末尾分节符产生的空节
        CTSectPr bodySectPr = doc.getDocument().getBody().getSectPr();
        if (bodySectPr != null) {
            sectionProps.add(bodySectPr);
        }
        while (sections.size() > 1 && sections.get(sections.size() - 1).getBlocks().isEmpty()) {
            sections.remove(sections.size() - 1);
        }
        for (int i = 0; i < sections.size(); i++) {
            CTSectPr prop = i < sectionProps.size() ? sectionProps.get(i) : bodySectPr;
            SectionMeta section = sections.get(i);
            section.setIndex(i);
            section.setPageSetup(parsePageSetup(prop));
            if (prop != null) {
                section.setBreakType(breakTypeOf(prop));
            }
            for (BlockMeta block : section.getBlocks()) {
                block.setSectionIndex(i);
            }
        }

        List<HeaderFooterMeta> headers = parseHeaderFooters(doc.getHeaderList(), "HEADER", images, notes);
        List<HeaderFooterMeta> footers = parseHeaderFooters(doc.getFooterList(), "FOOTER", images, notes);
        DocumentStylesMeta styles = parseStyles(doc);

        int characterCount = sections.stream()
                .flatMap(s -> s.getBlocks().stream())
                .mapToInt(b -> b.getParagraph() != null ? b.getParagraph().getText().length()
                        : b.getTable() != null ? tableTextLength(b.getTable()) : 0)
                .sum();
        characterCount += headers.stream().mapToInt(h -> h.getText().length()).sum();
        characterCount += footers.stream().mapToInt(f -> f.getText().length()).sum();

        DocumentStatistics statistics = DocumentStatistics.builder()
                .sectionCount(sections.size())
                .paragraphCount(paragraphIndex)
                .tableCount(tableIndex)
                .imageCount(images.size())
                .headerCount(headers.size())
                .footerCount(footers.size())
                .characterCount(characterCount)
                .build();

        return DocumentMeta.builder()
                .metadataVersion(6)
                .fileName(fileName)
                .parsedAt(LocalDateTime.now())
                .statistics(statistics)
                .sections(sections)
                .headers(headers)
                .footers(footers)
                .images(images)
                .comments(comments)
                .styles(styles)
                .notes(notes.isEmpty() ? null : String.join("；", notes))
                .build();
    }

    private ParagraphMeta parseParagraph(XWPFParagraph p, int index, int sectionIndex,
                                         List<ImageMeta> images, List<String> notes) {
        return parseParagraph(p, index, sectionIndex,
                new ImageContainer("body", null, null, null, null, null), images, notes);
    }

    /**
     * 收集段落上的 Word 批注。批注范围以 w:commentRangeStart 标记，起始段落即批注锚点；
     * 表格/页眉页脚批注暂不展开，避免同一批注在多个容器重复计数。
     */
    private void collectComments(XWPFParagraph p, int paragraphIndex, int sectionIndex,
                                 XWPFDocument doc, List<CommentMeta> comments) {
        if (p.getCTP() == null || p.getCTP().getCommentRangeStartList() == null) {
            return;
        }
        for (CTMarkupRange range : p.getCTP().getCommentRangeStartList()) {
            if (range.getId() == null) {
                continue;
            }
            String id = range.getId().toString();
            XWPFComment comment;
            try {
                comment = doc.getCommentByID(id);
            } catch (RuntimeException e) {
                continue;
            }
            if (comment == null) {
                continue;
            }
            LocalDateTime date = comment.getDate() == null ? null
                    : LocalDateTime.ofInstant(comment.getDate().toInstant(), ZoneId.systemDefault());
            comments.add(CommentMeta.builder()
                    .id(id)
                    .author(comment.getAuthor())
                    .initials(comment.getInitials())
                    .date(date)
                    .text(normalizeCommentText(comment.getText()))
                    .paragraphIndex(paragraphIndex)
                    .sectionIndex(sectionIndex)
                    .anchorPath("body.p[" + paragraphIndex + "]")
                    .build());
        }
    }

    private String normalizeCommentText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private ParagraphMeta parseParagraph(XWPFParagraph p, int index, int sectionIndex,
                                         ImageContainer container,
                                         List<ImageMeta> images, List<String> notes) {
        CTP ctp = p.getCTP();
        CTPPr pPr = ctp.getPPr();

        List<RunMeta> runs = new ArrayList<>();
        int runIndex = 0;
        boolean mixedFont = false;
        boolean mixedFontSize = false;
        String firstFont = null;
        Double firstSize = null;
        boolean pageNumberField = false;

        for (XWPFRun run : p.getRuns()) {
            RunMeta runMeta = parseRun(p, run, runIndex++);
            runs.add(runMeta);
            if (firstFont == null) {
                firstFont = runMeta.getFontEastAsia();
            } else if (!Objects.equals(firstFont, runMeta.getFontEastAsia())) {
                mixedFont = true;
            }
            if (firstSize == null) {
                firstSize = runMeta.getFontSizePt();
            } else if (runMeta.getFontSizePt() != null && !firstSize.equals(runMeta.getFontSizePt())) {
                mixedFontSize = true;
            }
            if (hasPageField(run)) {
                pageNumberField = true;
            }
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                ImageMeta image = parseImage(picture, images.size(), sectionIndex, index, run, container);
                if (image != null) {
                    images.add(image);
                }
            }
        }
        if (p.getText().toUpperCase(Locale.ROOT).contains("PAGE")) {
            pageNumberField = true;
        }

        String text = p.getText() == null ? "" : p.getText();
        int fullWidth = 0;
        int halfWidth = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isFullWidth(cp)) {
                fullWidth++;
            } else if (cp < 128) {
                halfWidth++;
            }
            i += Character.charCount(cp);
        }

        return ParagraphMeta.builder()
                .index(index)
                .styleId(p.getStyleID())
                .styleName(styleName(p))
                .text(text)
                .alignment(effectiveAlignment(p))
                .outlineLevel(effectiveOutlineLevel(p))
                .numbering(parseNumbering(p))
                .indentation(parseIndent(p))
                .spacing(parseSpacing(p))
                .borders(parseParagraphBorders(pPr))
                .shading(parseShading(pPr == null ? null : pPr.getShd()))
                .keepNext(pPr != null && pPr.isSetKeepNext())
                .pageBreakBefore(pPr != null && pPr.isSetPageBreakBefore())
                .widowControl(pPr == null || !pPr.isSetWidowControl())
                .runCount(runs.size())
                .runs(runs)
                .mixedFont(mixedFont)
                .mixedFontSize(mixedFontSize)
                .fullWidthCharCount(fullWidth)
                .halfWidthCharCount(halfWidth)
                .hasCjkPunctuation(hasCjkPunctuation(text))
                .hasPageNumberField(pageNumberField)
                .build();
    }

    private RunMeta parseRun(XWPFParagraph p, XWPFRun run, int index) {
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

    private RunFonts effectiveRunFonts(XWPFParagraph p, XWPFRun run) {
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

    private void addStyleRunProperties(XWPFStyles styles, String styleId,
                                       List<CTRPr> chain, Set<String> seen) {
        addStyleRunProperties(styles, styleId == null ? null : styles.getStyle(styleId), chain, seen);
    }

    private void addStyleRunProperties(XWPFStyles styles, XWPFStyle style,
                                       List<CTRPr> chain, Set<String> seen) {
        while (style != null && style.getStyleId() != null && seen.add(style.getStyleId())) {
            if (style.getCTStyle() != null && style.getCTStyle().getRPr() != null) {
                chain.add(style.getCTStyle().getRPr());
            }
            String basedOn = style.getBasisStyleID();
            style = basedOn == null ? null : styles.getStyle(basedOn);
        }
    }

    private String anchorWrapType(CTAnchor anchor) {
        try {
            var children = anchor.getDomNode().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                var node = children.item(i);
                if (node.getNodeType() != 1) {
                    continue;
                }
                String name = node.getLocalName();
                if (name == null) {
                    name = node.getNodeName();
                    int colon = name.indexOf(':');
                    if (colon >= 0) {
                        name = name.substring(colon + 1);
                    }
                }
                switch (name) {
                    case "wrapSquare" -> { return "square"; }
                    case "wrapTight" -> { return "tight"; }
                    case "wrapThrough" -> { return "through"; }
                    case "wrapTopAndBottom" -> { return "topAndBottom"; }
                    case "wrapNone" -> { return "none"; }
                    default -> { }
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "anchor";
    }

    private ImageMeta parseImage(XWPFPicture picture, int index, int sectionIndex, int paragraphIndex,
                                 XWPFRun run, ImageContainer container) {
        try {
            CTPicture ctPicture = picture.getCTPicture();
            double width = 0;
            double height = 0;
            if (ctPicture.getSpPr() != null && ctPicture.getSpPr().getXfrm() != null
                    && ctPicture.getSpPr().getXfrm().getExt() != null) {
                width = emuToPt(ctPicture.getSpPr().getXfrm().getExt().getCx());
                height = emuToPt(ctPicture.getSpPr().getXfrm().getExt().getCy());
            }
            String wrapType = "inline";
            boolean behindText = false;
            for (CTDrawing drawing : run.getCTR().getDrawingList()) {
                if (drawing.sizeOfAnchorArray() > 0) {
                    CTAnchor anchor = drawing.getAnchorArray(0);
                    behindText = Boolean.TRUE.equals(anchor.getBehindDoc());
                    // poi-ooxml-lite 不包含 CTWrapTight/CTWrapThrough/CTWrapTopBottom，
                    // 直接读 DOM 子元素，避免编译期依赖这些可选 schema 类。
                    wrapType = anchorWrapType(anchor);
                    break;
                }
                if (drawing.sizeOfInlineArray() > 0) {
                    wrapType = "inline";
                    break;
                }
            }
            XWPFPictureData pictureData = picture.getPictureData();
            String contentType = pictureData == null || pictureData.getPackagePart() == null
                    ? null : pictureData.getPackagePart().getContentType();
            String partName = pictureData == null || pictureData.getPackagePart() == null
                    ? null : pictureData.getPackagePart().getPartName().getName();
            return ImageMeta.builder()
                    .index(index)
                    .sectionIndex(sectionIndex)
                    .paragraphIndex(paragraphIndex)
                    .containerType(container.type())
                    .headerIndex(container.headerIndex())
                    .footerIndex(container.footerIndex())
                    .tableIndex(container.tableIndex())
                    .rowIndex(container.rowIndex())
                    .cellIndex(container.cellIndex())
                    .widthPt(width)
                    .heightPt(height)
                    .wrapType(wrapType)
                    .behindText(behindText)
                    .name(pictureData == null ? null : pictureData.getFileName())
                    .contentType(contentType)
                    .partName(partName)
                    .altText(picture.getDescription())
                    .build();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private TableMeta parseTable(XWPFTable table, int index, int paragraphIndex, int sectionIndex,
                                 List<ImageMeta> images, List<String> notes) {
        CTTblPr tblPr = table.getCTTbl().getTblPr();
        double widthPt = 0;
        String widthType = null;
        String alignment = null;
        String layout = null;
        Map<String, String> borders = new LinkedHashMap<>();
        if (tblPr != null) {
            if (tblPr.getTblW() != null) {
                widthPt = twipsToPt(tblPr.getTblW().getW());
                widthType = tblPr.getTblW().getType() == null ? null
                        : tblPr.getTblW().getType().toString().toLowerCase(Locale.ROOT);
            }
            if (tblPr.getJc() != null && tblPr.getJc().getVal() != null) {
                alignment = tblPr.getJc().getVal().toString().toLowerCase(Locale.ROOT);
            }
            if (tblPr.getTblLayout() != null && tblPr.getTblLayout().getType() != null) {
                layout = tblPr.getTblLayout().getType().toString().toLowerCase(Locale.ROOT);
            }
            borders = parseTableBorders(tblPr.getTblBorders());
        }

        List<TableRowMeta> rows = new ArrayList<>();
        boolean headerRow = false;
        int rowCount = table.getRows() == null ? 0 : table.getRows().size();
        for (int r = 0; r < rowCount; r++) {
            XWPFTableRow row = table.getRow(r);
            CTRow ctRow = row.getCtRow();
            boolean repeatHeader = ctRow.getTrPr() != null && ctRow.getTrPr().sizeOfTblHeaderArray() > 0;
            if (r == 0) {
                headerRow = repeatHeader;
            }
            List<TableCellMeta> cells = new ArrayList<>();
            List<XWPFTableCell> tableCells = row.getTableCells();
            for (int c = 0; c < tableCells.size(); c++) {
                cells.add(parseCell(tableCells.get(c), c, index, r, sectionIndex, images));
            }
            rows.add(TableRowMeta.builder()
                    .index(r)
                    .heightPt(twipsToPt(row.getHeight()))
                    .repeatHeader(repeatHeader)
                    .cells(cells)
                    .build());
        }

        // XWPFTable 没有 getNumberOfColumns()，按实际单元格数的最大值近似列数（合并单元格会少算）。
        int columnCount = rows.stream().mapToInt(r -> r.getCells() == null ? 0 : r.getCells().size()).max().orElse(0);

        return TableMeta.builder()
                .index(index)
                .paragraphIndex(paragraphIndex)
                .widthPt(widthPt)
                .widthType(widthType)
                .alignment(alignment)
                .layout(layout)
                .borders(borders.isEmpty() ? null : borders)
                .headerRow(headerRow)
                .rowCount(rowCount)
                .columnCount(columnCount)
                .rows(rows)
                .build();
    }

    private TableCellMeta parseCell(XWPFTableCell cell, int index, int tableIndex, int rowIndex,
                                    int sectionIndex, List<ImageMeta> images) {
        CTTcPr tcPr = cell.getCTTc().getTcPr();
        int gridSpan = 1;
        String vMerge = null;
        double widthPt = 0;
        String shading = null;
        if (tcPr != null) {
            if (tcPr.getGridSpan() != null && tcPr.getGridSpan().getVal() != null) {
                gridSpan = tcPr.getGridSpan().getVal().intValue();
            }
            if (tcPr.getVMerge() != null && tcPr.getVMerge().getVal() != null) {
                vMerge = tcPr.getVMerge().getVal().toString().toLowerCase(Locale.ROOT);
            }
            if (tcPr.getTcW() != null && tcPr.getTcW().getW() != null) {
                widthPt = twipsToPt(tcPr.getTcW().getW());
            }
            shading = parseShading(tcPr.getShd());
        }
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        XWPFParagraph first = paragraphs.isEmpty() ? null : paragraphs.get(0);
        Double fontSize = null;
        Boolean bold = null;
        if (first != null && !first.getRuns().isEmpty()) {
            try {
                fontSize = first.getRuns().get(0).getFontSizeAsDouble();
            } catch (RuntimeException ignored) {
                // ignore malformed font size
            }
            bold = first.getRuns().get(0).isBold();
        }
        List<ImageMeta> cellImages = new ArrayList<>();
        ImageContainer container = new ImageContainer(
                "tableCell", null, null, tableIndex, rowIndex, index);
        for (XWPFParagraph paragraph : paragraphs) {
            for (XWPFRun run : paragraph.getRuns()) {
                for (XWPFPicture picture : run.getEmbeddedPictures()) {
                    ImageMeta image = parseImage(picture, images.size(), sectionIndex,
                            -1, run, container);
                    if (image != null) {
                        images.add(image);
                        cellImages.add(image);
                    }
                }
            }
        }
        return TableCellMeta.builder()
                .index(index)
                .text(cell.getText())
                .gridSpan(gridSpan)
                .vMerge(vMerge)
                .widthPt(widthPt)
                .verticalAlign(cell.getVerticalAlignment() == null ? null
                        : cell.getVerticalAlignment().toString().toLowerCase(Locale.ROOT))
                .alignment(first == null || first.getAlignment() == null ? null
                        : first.getAlignment().toString().toLowerCase(Locale.ROOT))
                .shading(shading)
                .paragraphCount(paragraphs.size())
                .fontSizePt(fontSize)
                .bold(bold)
                .images(cellImages.isEmpty() ? null : cellImages)
                .build();
    }

    private List<HeaderFooterMeta> parseHeaderFooters(List<? extends XWPFHeaderFooter> list, String type,
                                                       List<ImageMeta> images, List<String> notes) {
        List<HeaderFooterMeta> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (int i = 0; i < list.size(); i++) {
            XWPFHeaderFooter hf = list.get(i);
            List<ParagraphMeta> paragraphs = new ArrayList<>();
            boolean hasPageNumber = false;
            for (int j = 0; j < hf.getParagraphs().size(); j++) {
                XWPFParagraph p = hf.getParagraphs().get(j);
                boolean header = "HEADER".equals(type);
                ImageContainer container = new ImageContainer(
                        header ? "header" : "footer",
                        header ? i : null,
                        header ? null : i,
                        null, null, null);
                ParagraphMeta meta = parseParagraph(p, j, 0, container, images, notes);
                paragraphs.add(meta);
                hasPageNumber |= meta.isHasPageNumberField();
            }
            String text = hf.getText() == null ? "" : hf.getText();
            result.add(HeaderFooterMeta.builder()
                    .index(i)
                    .type(type)
                    .sectionIndex(0)
                    .headerFooterType(headerFooterType(hf, i))
                    .text(text)
                    .hasPageNumberField(hasPageNumber || text.toUpperCase(Locale.ROOT).contains("PAGE"))
                    .paragraphs(paragraphs)
                    .build());
        }
        return result;
    }

    private String headerFooterType(XWPFHeaderFooter hf, int index) {
        // POI 不同小版本对 getHeaderFooterType 的返回类型有差异，按常见顺序兜底，避免解析失败
        String name = hf.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (name.contains("first")) {
            return "first";
        }
        if (name.contains("even")) {
            return "even";
        }
        return index % 3 == 1 ? "first" : index % 3 == 2 ? "even" : "default";
    }

    private DocumentStylesMeta parseStyles(XWPFDocument doc) {
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

    private void collectParagraphStyleIds(List<XWPFParagraph> paragraphs, Set<String> target) {
        if (paragraphs == null) {
            return;
        }
        for (XWPFParagraph paragraph : paragraphs) {
            if (paragraph.getStyleID() != null && !paragraph.getStyleID().isBlank()) {
                target.add(paragraph.getStyleID());
            }
        }
    }

    private PageSetupMeta parsePageSetup(CTSectPr sectPr) {
        if (sectPr == null) {
            return PageSetupMeta.builder().orientation("portrait").columnCount(1).build();
        }
        double width = 0;
        double height = 0;
        String orientation = "portrait";
        if (sectPr.getPgSz() != null) {
            CTPageSz pageSz = sectPr.getPgSz();
            width = twipsToPt(pageSz.getW());
            height = twipsToPt(pageSz.getH());
            if (pageSz.getOrient() != null) {
                orientation = pageSz.getOrient().toString().toLowerCase(Locale.ROOT);
            }
        }
        double top = 0;
        double bottom = 0;
        double left = 0;
        double right = 0;
        double header = 0;
        double footer = 0;
        double gutter = 0;
        if (sectPr.getPgMar() != null) {
            CTPageMar mar = sectPr.getPgMar();
            top = twipsToPt(mar.getTop());
            bottom = twipsToPt(mar.getBottom());
            left = twipsToPt(mar.getLeft());
            right = twipsToPt(mar.getRight());
            header = twipsToPt(mar.getHeader());
            footer = twipsToPt(mar.getFooter());
            gutter = twipsToPt(mar.getGutter());
        }
        int columnCount = 1;
        double columnSpace = 0;
        if (sectPr.getCols() != null) {
            CTColumns cols = sectPr.getCols();
            if (cols.getNum() != null) {
                columnCount = cols.getNum().intValue();
            }
            // w:cols/@w:space 即栏间距；CTColumn 在 poi-ooxml-lite 5.2.5 中缺失，不引用。
            columnSpace = twipsToPt(cols.getSpace());
        }
        List<String> headerTypes = new ArrayList<>();
        List<String> footerTypes = new ArrayList<>();
        for (CTHdrFtrRef ref : sectPr.getHeaderReferenceList()) {
            if (ref.getType() != null) {
                headerTypes.add(ref.getType().toString().toLowerCase(Locale.ROOT));
            }
        }
        for (CTHdrFtrRef ref : sectPr.getFooterReferenceList()) {
            if (ref.getType() != null) {
                footerTypes.add(ref.getType().toString().toLowerCase(Locale.ROOT));
            }
        }
        return PageSetupMeta.builder()
                .widthPt(width)
                .heightPt(height)
                .orientation(orientation)
                .topMarginPt(top)
                .bottomMarginPt(bottom)
                .leftMarginPt(left)
                .rightMarginPt(right)
                .headerDistancePt(header)
                .footerDistancePt(footer)
                .gutterPt(gutter)
                .columnCount(columnCount)
                .columnSpacePt(columnSpace)
                .titlePage(sectPr.getTitlePg() != null)
                .differentFirstPage(sectPr.getTitlePg() != null)
                .differentOddEven(false)
                .headerTypes(headerTypes)
                .footerTypes(footerTypes)
                .build();
    }

    private NumberingMeta parseNumbering(XWPFParagraph p) {
        if (p.getNumID() == null) {
            return null;
        }
        return NumberingMeta.builder()
                .numId(p.getNumID().longValue())
                .ilvl(p.getNumIlvl() == null ? 0 : p.getNumIlvl().intValue())
                .numFmt(p.getNumFmt())
                .levelText(null)
                .start(null)
                .suffix(null)
                .label(p.getNumFmt())
                .build();
    }

    /**
     * 段落属性链：直接格式 → 段落样式 → 基于样式 → 默认正文样式。
     * Word 中大量段落的缩进、行距、对齐只写在样式里（例如默认“正文”样式
     * 定义了首行缩进 2 字符），POI 的段落级 getter 不会自动展开这些继承值。
     */
    private List<Object> paragraphPropChain(XWPFParagraph p) {
        List<Object> chain = new ArrayList<>();
        if (p.getCTP() != null && p.getCTP().getPPr() != null) {
            chain.add(p.getCTP().getPPr());
        }
        XWPFDocument doc = p.getDocument();
        if (doc == null || doc.getStyles() == null) {
            return chain;
        }
        Set<String> seen = new LinkedHashSet<>();
        XWPFStyle style = p.getStyle() == null ? null : doc.getStyles().getStyle(p.getStyle());
        if (style == null) {
            style = doc.getStyles().getStyleWithName("Normal");
        }
        while (style != null && style.getStyleId() != null && seen.add(style.getStyleId())) {
            if (style.getCTStyle() != null && style.getCTStyle().getPPr() != null) {
                chain.add(style.getCTStyle().getPPr());
            }
            String basedOn = style.getBasisStyleID();
            style = basedOn == null ? null : doc.getStyles().getStyle(basedOn);
        }
        return chain;
    }

    private CTInd indentOf(Object pPr) {
        if (pPr instanceof CTPPr p) {
            return p.getInd();
        }
        if (pPr instanceof CTPPrGeneral p) {
            return p.getInd();
        }
        return null;
    }

    private CTJc justificationOf(Object pPr) {
        if (pPr instanceof CTPPr p) {
            return p.getJc();
        }
        if (pPr instanceof CTPPrGeneral p) {
            return p.getJc();
        }
        return null;
    }

    private CTSpacing spacingOf(Object pPr) {
        if (pPr instanceof CTPPr p) {
            return p.getSpacing();
        }
        if (pPr instanceof CTPPrGeneral p) {
            return p.getSpacing();
        }
        return null;
    }

    private CTDecimalNumber outlineOf(Object pPr) {
        if (pPr instanceof CTPPr p) {
            return p.getOutlineLvl();
        }
        if (pPr instanceof CTPPrGeneral p) {
            return p.getOutlineLvl();
        }
        return null;
    }

    private CTInd firstIndent(List<Object> chain) {
        for (Object pPr : chain) {
            CTInd value = indentOf(pPr);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private CTJc firstJustification(List<Object> chain) {
        for (Object pPr : chain) {
            CTJc value = justificationOf(pPr);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private CTSpacing firstSpacing(List<Object> chain) {
        for (Object pPr : chain) {
            CTSpacing value = spacingOf(pPr);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private CTDecimalNumber firstOutline(List<Object> chain) {
        for (Object pPr : chain) {
            CTDecimalNumber value = outlineOf(pPr);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String effectiveAlignment(XWPFParagraph p) {
        CTJc jc = firstJustification(paragraphPropChain(p));
        if (jc != null && jc.getVal() != null) {
            return jc.getVal().toString().toLowerCase(Locale.ROOT);
        }
        return p.getAlignment() == null ? null : p.getAlignment().toString().toLowerCase(Locale.ROOT);
    }

    private Integer effectiveOutlineLevel(XWPFParagraph p) {
        CTDecimalNumber outline = firstOutline(paragraphPropChain(p));
        return outline == null ? null : outline.getVal() == null ? null : outline.getVal().intValue();
    }

    private IndentMeta parseIndent(XWPFParagraph p) {
        IndentMeta meta = IndentMeta.builder().build();
        CTInd ind = firstIndent(paragraphPropChain(p));
        if (ind == null) {
            return meta;
        }
        meta.setLeftPt(twipsToPt(ind.getLeft()));
        meta.setRightPt(twipsToPt(ind.getRight()));
        meta.setFirstLinePt(twipsToPt(ind.getFirstLine()));
        meta.setHangingPt(twipsToPt(ind.getHanging()));
        if (ind.getLeftChars() != null) {
            meta.setLeftChars(ind.getLeftChars().intValue());
        }
        if (ind.getFirstLineChars() != null) {
            meta.setFirstLineChars(ind.getFirstLineChars().intValue());
        }
        if (ind.getHangingChars() != null) {
            meta.setHangingChars(ind.getHangingChars().intValue());
        }
        return meta;
    }

    private ParagraphSpacingMeta parseSpacing(XWPFParagraph p) {
        CTSpacing spacing = firstSpacing(paragraphPropChain(p));
        double before = spacing == null || spacing.getBefore() == null
                ? 0 : twipsToPt(spacing.getBefore());
        double after = spacing == null || spacing.getAfter() == null
                ? 0 : twipsToPt(spacing.getAfter());

        Double between = p.getSpacingBetween();
        if (between != null && between <= 0) {
            between = null;
        }
        LineSpacingRule rule = p.getSpacingLineRule();
        String ruleName = rule == null ? "auto" : switch (rule) {
            case EXACT -> "exact";
            case AT_LEAST -> "atLeast";
            default -> "auto";
        };

        // 样式中的行距不会被 p.getSpacingBetween() 展开；从 pPr 的 w:spacing 读取。
        if (spacing != null && spacing.getLine() != null) {
            double raw = asDouble(spacing.getLine());
            String rawRule = spacing.getLineRule() == null
                    ? "auto" : spacing.getLineRule().toString().toLowerCase(Locale.ROOT);
            if ("auto".equals(rawRule)) {
                between = raw / 240.0;
                ruleName = "auto";
            } else {
                between = raw / 20.0;
                ruleName = "atLeast".equals(rawRule) ? "atLeast" : "exact";
            }
        }

        Double lines = null;
        Double pt = null;
        if (between != null) {
            if ("auto".equals(ruleName)) {
                lines = between;
                pt = between * 12.0;
            } else {
                pt = between;
                lines = between / 12.0;
            }
        } else if ("auto".equals(ruleName)) {
            lines = 1.0;
            pt = 12.0;
        }
        return ParagraphSpacingMeta.builder()
                .beforePt(before)
                .afterPt(after)
                .lineSpacingRaw(between)
                .lineSpacingRule(ruleName)
                .lineSpacingPt(pt)
                .lineSpacingLines(lines)
                .build();
    }

    private Map<String, String> parseParagraphBorders(CTPPr pPr) {
        if (pPr == null || pPr.getPBdr() == null) {
            return null;
        }
        CTPBdr borders = pPr.getPBdr();
        Map<String, String> result = new LinkedHashMap<>();
        putBorder(result, "top", borders.getTop());
        putBorder(result, "bottom", borders.getBottom());
        putBorder(result, "left", borders.getLeft());
        putBorder(result, "right", borders.getRight());
        return result.isEmpty() ? null : result;
    }

    private Map<String, String> parseTableBorders(CTTblBorders borders) {
        if (borders == null) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        putBorder(result, "top", borders.getTop());
        putBorder(result, "bottom", borders.getBottom());
        putBorder(result, "left", borders.getLeft());
        putBorder(result, "right", borders.getRight());
        putBorder(result, "insideH", borders.getInsideH());
        putBorder(result, "insideV", borders.getInsideV());
        return result;
    }

    private void putBorder(Map<String, String> target, String key, CTBorder border) {
        if (border == null) {
            return;
        }
        // CTBorder.getColor() 在 XmlBeans 中可能是 byte[]，不能直接 String 拼接，否则会得到 [B@xxx。
        String color = colorHex(border.getColor());
        String value = (border.getVal() == null ? "single" : border.getVal().toString().toLowerCase(Locale.ROOT))
                + " " + (border.getSz() == null ? "4" : border.getSz())
                + " " + (color == null ? "auto" : color);
        target.put(key, value);
    }

    private String parseShading(CTShd shd) {
        if (shd == null) {
            return null;
        }
        String fill = colorHex(shd.getFill());
        String val = shd.getVal() == null ? "clear" : shd.getVal().toString().toLowerCase(Locale.ROOT);
        return val + (fill == null ? "" : " " + fill);
    }

    private Integer outlineLevel(CTPPr pPr) {
        if (pPr == null || pPr.getOutlineLvl() == null || pPr.getOutlineLvl().getVal() == null) {
            return null;
        }
        return pPr.getOutlineLvl().getVal().intValue();
    }

    private String styleName(XWPFParagraph p) {
        try {
            if (p.getStyleID() == null || p.getDocument() == null || p.getDocument().getStyles() == null) {
                return null;
            }
            XWPFStyle style = p.getDocument().getStyles().getStyle(p.getStyleID());
            return style == null ? null : style.getName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private CTSectPr sectPrOf(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().getPPr();
        return pPr == null ? null : pPr.getSectPr();
    }

    private String breakTypeOf(CTSectPr sectPr) {
        if (sectPr == null || sectPr.getType() == null) {
            return "nextPage";
        }
        String value = sectPr.getType().toString().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "continuous" -> "continuous";
            case "evenpage" -> "evenPage";
            case "oddpage" -> "oddPage";
            default -> "nextPage";
        };
    }

    private boolean hasPageField(XWPFRun run) {
        try {
            String text = run.text();
            if (text != null && text.toUpperCase(Locale.ROOT).contains("PAGE")) {
                return true;
            }
            // POI 5.2.x 的 CTR 没有 FldSimple 便捷方法，instrText 的元素类型是 CTText。
            for (CTText instr : run.getCTR().getInstrTextList()) {
                if (instr.getStringValue() != null
                        && instr.getStringValue().toUpperCase(Locale.ROOT).contains("PAGE")) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // 容错：字段结构异常不影响正文分析
        }
        return false;
    }

    private int tableTextLength(TableMeta table) {
        if (table == null || table.getRows() == null) {
            return 0;
        }
        int length = 0;
        for (TableRowMeta row : table.getRows()) {
            if (row.getCells() != null) {
                for (TableCellMeta cell : row.getCells()) {
                    length += cell.getText() == null ? 0 : cell.getText().length();
                }
            }
        }
        return length;
    }
}
