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
public class ParagraphMeta {

    /** 全局段落序号，从 0 开始，用于 anchorPath=body.p[n] */
    private int index;

    private String styleId;
    private String styleName;

    private String text;
    private String alignment;

    private Integer outlineLevel;

    private NumberingMeta numbering;

    private IndentMeta indentation;

    private ParagraphSpacingMeta spacing;

    /** 段落边框：top/bottom/left/right -> "single 4 #000000" */
    private Map<String, String> borders;

    /** 底纹，例如 "clear #F2F2F2" */
    private String shading;

    private boolean keepNext;
    private boolean pageBreakBefore;
    private boolean widowControl;

    private int runCount;
    private List<RunMeta> runs;

    private boolean mixedFont;
    private boolean mixedFontSize;
    private int fullWidthCharCount;
    private int halfWidthCharCount;
    private boolean hasCjkPunctuation;
    private boolean hasPageNumberField;
}
