package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMeta {

    /** 全局表格序号，从 0 开始，用于 anchorPath=body.table[n] */
    private int index;

    private int paragraphIndex;

    private double widthPt;
    private String widthType;
    private String alignment;
    private String layout;

    /** 表格边框：top/bottom/left/right/insideH/insideV -> "single 4 #000000" */
    private Map<String, String> borders;

    private boolean headerRow;
    private int rowCount;
    private int columnCount;
    private List<TableRowMeta> rows;
}
