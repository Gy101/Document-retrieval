package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndentMeta {

    private double leftPt;
    private double rightPt;
    private double firstLinePt;
    private double hangingPt;

    private Integer leftChars;
    private Integer firstLineChars;
    private Integer hangingChars;
}
