package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParagraphSpacingMeta {

    private double beforePt;
    private double afterPt;

    /** 原始行距值：AUTO 时为 240 分之一行，EXACT/AT_LEAST 时为 twips */
    private Double lineSpacingRaw;

    /** auto / exact / atLeast */
    private String lineSpacingRule;

    /** 统一换算后的行距（磅）；auto 时按单倍行距约 12 磅估算，仅用于展示 */
    private Double lineSpacingPt;

    /** 统一换算后的行距（倍） */
    private Double lineSpacingLines;
}
