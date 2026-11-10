package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatistics {

    private int sectionCount;
    private int paragraphCount;
    private int tableCount;
    private int imageCount;
    private int headerCount;
    private int footerCount;
    private int characterCount;
}
