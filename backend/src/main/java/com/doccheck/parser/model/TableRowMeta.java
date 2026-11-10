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
public class TableRowMeta {

    private int index;

    private double heightPt;

    private boolean repeatHeader;

    private List<TableCellMeta> cells;
}
