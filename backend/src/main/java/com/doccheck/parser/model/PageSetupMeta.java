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
public class PageSetupMeta {

    private double widthPt;
    private double heightPt;
    private String orientation;

    private double topMarginPt;
    private double bottomMarginPt;
    private double leftMarginPt;
    private double rightMarginPt;
    private double headerDistancePt;
    private double footerDistancePt;
    private double gutterPt;

    private int columnCount;
    private double columnSpacePt;

    private boolean titlePage;
    private boolean differentFirstPage;
    private boolean differentOddEven;

    /** 该节引用的页眉类型：default / first / even */
    private List<String> headerTypes;

    /** 该节引用的页脚类型：default / first / even */
    private List<String> footerTypes;
}
