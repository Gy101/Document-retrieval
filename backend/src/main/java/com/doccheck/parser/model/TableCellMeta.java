package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableCellMeta {

    private int index;

    private String text;

    private int gridSpan;

    /** restart / continue / null */
    private String vMerge;

    private double widthPt;

    private String verticalAlign;

    private String alignment;

    private String shading;

    private int paragraphCount;

    /** 单元格内首段的有效字号（用于表头字号判断） */
    private Double fontSizePt;

    /** 单元格内首段是否加粗 */
    private Boolean bold;

    /** 单元格内的图片，前端按 body.table[t].r[r].c[c] 定位 */
    private List<ImageMeta> images;
}
