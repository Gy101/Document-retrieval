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
public class DocumentStylesMeta {

    private String defaultFontEastAsia;
    private String defaultFontAscii;
    private Double defaultFontSizePt;

    private List<StyleMeta> paragraphStyles;
}
