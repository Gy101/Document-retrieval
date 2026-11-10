package com.doccheck.check.strategy;

import com.doccheck.check.*;
import com.doccheck.domain.enums.Severity;
import com.doccheck.domain.model.FormatDiff;
import com.doccheck.parser.model.TableCellMeta;
import com.doccheck.parser.model.TableMeta;
import com.doccheck.parser.model.TableRowMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * 表格格式策略。
 *
 * <p>只比较“版式”而不比较“数据规模”：表格行数、列数、列名和单元格数量由文档内容决定，
 * 模板里的表格往往只是示例（例如参与者词汇表只列了 3 行），实际文档可以按业务增减行/列，
 * 不能因为 3 行变成 4 行、3 列变成 5 列就判为格式错误。</p>
 *
 * <p>真正需要保持一致的是：表格整体对齐/布局、边框、表头跨页重复，以及表头/正文区域的
 * 底纹、加粗、垂直对齐等格式模式。单元格文字对齐受 Word/WPS 表格样式影响较大，
 * 不作为格式差异输出。列数不同时不再按列位置强行配对单元格。</p>
 */
@Service
public class TableFormatStrategy implements FormatCheckStrategy {

    private static final int MAX_TABLE_PAIRS = 20;
    private static final int MAX_DIFFS = 80;

    @Override
    public String name() {
        return "table-format";
    }

    @Override
    public String dimension() {
        return "表格格式";
    }

    @Override
    public String promptGuidance() {
        return "表格只比较版式：整体对齐、表格布局、边框、表头是否跨页重复，以及表头/正文区域的"
                + "底纹、加粗、垂直对齐等格式模式。单元格文字对齐受 Word/WPS 表格样式影响，不作为差异。"
                + "表格行数、列数、列标题和数据条数由文档内容决定，模板中的表格行只是示例，不得仅因"
                + "行数、列数或列名不同判为格式差异；列数不同时不要按列位置强行比较单元格。";
    }

    @Override
    public List<FormatDiff> precheck(CheckContext context) {
        List<FormatDiff> result = new ArrayList<>();
        List<TableMeta> template = MetaIndex.tables(context.getTemplateMeta());
        List<TableMeta> target = MetaIndex.tables(context.getTargetMeta());
        int limit = Math.min(MAX_TABLE_PAIRS, Math.min(template.size(), target.size()));
        for (int i = 0; i < limit && result.size() < MAX_DIFFS; i++) {
            TableMeta tt = template.get(i);
            TableMeta dt = target.get(i);
            String anchor = DiffSupport.anchorOfTable(dt.getIndex());
            String location = "第 " + (dt.getIndex() + 1) + " 个表格";

            // 表头重复属于版式规则，而不是内容规模。
            if (tt.isHeaderRow() != dt.isHeaderRow()) {
                result.add(DiffSupport.of("表格", Severity.MIDDLE, "table", anchor, location,
                        tt.isHeaderRow() ? "表头跨页重复" : "表头不重复",
                        dt.isHeaderRow() ? "表头跨页重复" : "表头不重复", "",
                        "按模板设置表格首行跨页重复"));
            }
            if (!Objects.equals(normalizeAlignment(tt.getAlignment()), normalizeAlignment(dt.getAlignment()))) {
                result.add(DiffSupport.of("表格", Severity.LOW, "table", anchor, location,
                        "表格对齐 " + display(tt.getAlignment()), "表格对齐 " + display(dt.getAlignment()), "",
                        "调整表格整体对齐方式"));
            }
            if (!Objects.equals(normalizeLayout(tt.getLayout()), normalizeLayout(dt.getLayout()))) {
                result.add(DiffSupport.of("表格", Severity.MIDDLE, "table", anchor, location,
                        "表格布局 " + displayLayout(tt.getLayout()), "表格布局 " + displayLayout(dt.getLayout()), "",
                        "按模板调整表格为固定列宽或自动调整"));
            }
            if (!bordersEqual(tt.getBorders(), dt.getBorders())) {
                result.add(DiffSupport.of("表格", Severity.MIDDLE, "table", anchor, location,
                        "边框 " + displayBorders(tt.getBorders()), "边框 " + displayBorders(dt.getBorders()), "",
                        "按模板统一表格边框线型与粗细"));
            }

            compareCellPattern(tt, dt, anchor, location, true, result);
            compareCellPattern(tt, dt, anchor, location, false, result);
        }
        return result;
    }

    /**
     * 比较表头或正文区域的“格式模式”，而不是逐单元格按位置比较。
     * 同一区域可能出现多种对齐/底纹（例如首列居中、说明列左对齐），因此用去重集合表达模式；
     * 集合一致即认为版式一致，行列数量不同不会影响结果。
     */
    private void compareCellPattern(TableMeta tt, TableMeta dt, String anchor, String location,
                                    boolean header, List<FormatDiff> result) {
        List<TableCellMeta> expected = header ? headerCells(tt) : bodyCells(tt);
        List<TableCellMeta> actual = header ? headerCells(dt) : bodyCells(dt);
        if (expected.isEmpty() || actual.isEmpty()) {
            return;
        }

        String area = header ? "表头" : "正文";
        String cellAnchor = header ? anchor + ".r[0].c[0]" : anchor;

        // 单元格文字对齐不作为差异：Word/WPS 会结合表格样式、单元格宽度和段落缩进
        // 重新排版，直接按 XML 的 w:jc 比较容易把视觉一致的表格误报为“左对齐/居中”。

        Set<String> expectedShading = pattern(expected, c -> normalizeShading(c.getShading()));
        Set<String> actualShading = pattern(actual, c -> normalizeShading(c.getShading()));
        if (!expectedShading.equals(actualShading)) {
            addPatternDiff(result, Severity.MIDDLE, cellAnchor, location,
                    area + "底纹 " + join(expectedShading), area + "底纹 " + join(actualShading),
                    "按模板设置" + area + "底纹");
        }

        Set<String> expectedBold = pattern(expected, c -> Boolean.TRUE.equals(c.getBold()) ? "加粗" : "常规");
        Set<String> actualBold = pattern(actual, c -> Boolean.TRUE.equals(c.getBold()) ? "加粗" : "常规");
        if (!expectedBold.equals(actualBold)) {
            addPatternDiff(result, Severity.MIDDLE, cellAnchor, location,
                    area + "字重 " + join(expectedBold), area + "字重 " + join(actualBold),
                    "按模板调整" + area + "字重");
        }

        Set<String> expectedVertical = pattern(expected, c -> normalizeVertical(c.getVerticalAlign()));
        Set<String> actualVertical = pattern(actual, c -> normalizeVertical(c.getVerticalAlign()));
        if (!expectedVertical.equals(actualVertical)) {
            addPatternDiff(result, Severity.LOW, cellAnchor, location,
                    area + "垂直对齐 " + join(expectedVertical), area + "垂直对齐 " + join(actualVertical),
                    "统一" + area + "单元格的垂直对齐方式");
        }

        // 字号只在列数相同时比较：列数不同时为容纳内容缩小字号是合理的排版选择，
        // 不应因此把一个语义不同的示例表判为格式错误。
        if (header && tt.getColumnCount() == dt.getColumnCount()) {
            Set<String> expectedFont = pattern(expected, c -> formatFontSize(c.getFontSizePt()));
            Set<String> actualFont = pattern(actual, c -> formatFontSize(c.getFontSizePt()));
            if (!expectedFont.equals(actualFont)) {
                addPatternDiff(result, Severity.MIDDLE, cellAnchor, location,
                        "表头字号 " + join(expectedFont), "表头字号 " + join(actualFont),
                        "按模板调整表头字号");
            }
        }
    }

    private void addPatternDiff(List<FormatDiff> result, String severity, String anchor, String location,
                                String expected, String actual, String suggestion) {
        if (result.size() >= MAX_DIFFS) {
            return;
        }
        result.add(DiffSupport.of("表格", severity, "table", anchor, location,
                expected, actual, "", suggestion));
    }

    private List<TableCellMeta> headerCells(TableMeta table) {
        if (table.getRows() == null || table.getRows().isEmpty()) {
            return List.of();
        }
        TableRowMeta first = table.getRows().get(0);
        return first.getCells() == null ? List.of() : first.getCells();
    }

    private List<TableCellMeta> bodyCells(TableMeta table) {
        if (table.getRows() == null || table.getRows().size() <= 1) {
            return List.of();
        }
        List<TableCellMeta> cells = new ArrayList<>();
        for (int i = 1; i < table.getRows().size(); i++) {
            TableRowMeta row = table.getRows().get(i);
            if (row != null && row.getCells() != null) {
                cells.addAll(row.getCells());
            }
        }
        return cells;
    }

    private <T> Set<String> pattern(List<TableCellMeta> cells, Function<TableCellMeta, String> mapper) {
        Set<String> values = new TreeSet<>();
        for (TableCellMeta cell : cells) {
            if (cell != null) {
                values.add(mapper.apply(cell));
            }
        }
        return values;
    }

    private String normalizeAlignment(String value) {
        return value == null || value.isBlank() ? "left" : value.toLowerCase();
    }

    private String normalizeLayout(String value) {
        return value == null || value.isBlank() ? "autofit" : value.toLowerCase();
    }

    private String normalizeShading(String value) {
        if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value) || "clear".equalsIgnoreCase(value)) {
            return "无底纹";
        }
        return value.toUpperCase();
    }

    private String normalizeVertical(String value) {
        return value == null || value.isBlank() ? "top" : value.toLowerCase();
    }

    private String formatFontSize(Double value) {
        return value == null ? "默认字号" : String.format(java.util.Locale.ROOT, "%.1fpt", value);
    }

    private boolean bordersEqual(Map<String, String> a, Map<String, String> b) {
        return Objects.equals(normalizeBorders(a), normalizeBorders(b));
    }

    /**
     * 只保留“看得见的边框”。w:val=nil/none 表示无边框，不能因为一张表显式写了 nil、
     * 另一张表没有 tblBorders 就判为不同——两者视觉上都是无边框。
     */
    private Map<String, String> normalizeBorders(Map<String, String> borders) {
        Map<String, String> result = new java.util.TreeMap<>();
        if (borders == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : borders.entrySet()) {
            String value = entry.getValue();
            if (value == null) {
                continue;
            }
            String normalized = value.trim().replaceAll("\\s+", " ").toLowerCase();
            if (normalized.startsWith("nil") || normalized.startsWith("none")
                    || normalized.startsWith("null")) {
                continue;
            }
            // w:color=auto 在 Word 中通常渲染为黑色，与显式 000000 视为同一种边框。
            normalized = normalized.replace(" auto", " 000000");
            result.put(entry.getKey(), normalized);
        }
        return result;
    }

    private String join(Set<String> values) {
        return values.isEmpty() ? "未设置" : String.join("、", new LinkedHashSet<>(values));
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "默认" : value;
    }

    private String displayLayout(String value) {
        return value == null || value.isBlank() ? "自动调整" : value;
    }

    private String displayBorders(Map<String, String> borders) {
        Map<String, String> normalized = normalizeBorders(borders);
        return normalized.isEmpty() ? "无边框" : normalized.toString();
    }
}
