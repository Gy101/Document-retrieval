package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleMeta {

    private String id;
    private String name;
    private String type;
    private String basedOn;

    private String fontEastAsia;
    private String fontAscii;
    private Double fontSizePt;
    private Boolean bold;

    private String alignment;
    private Double firstLinePt;
    private Double lineSpacingLines;
}
