package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字符级格式（一个 Word Run 即一段相同格式的连续字符）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunMeta {

    private int index;

    private String text;

    private String styleId;

    private String fontEastAsia;
    private String fontAscii;
    private String fontHAnsi;
    /** w:rFonts/@w:hint，决定中文标点等“歧义字符”使用东亚字体还是 ASCII 字体。 */
    private String fontHint;
    private Double fontSizePt;

    private Boolean bold;
    private Boolean italic;
    private String underline;
    private boolean strike;

    private String color;
    private String highlight;

    private Double characterSpacingPt;
    private Double kerningPt;
    private Double positionPt;
    private Integer textScalePercent;

    private boolean smallCaps;
    private boolean allCaps;
    private boolean hidden;
    private String verticalAlign;
}
