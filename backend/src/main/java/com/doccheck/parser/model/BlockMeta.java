package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 正文块。type=paragraph 时 paragraph 非空；type=table 时 table 非空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockMeta {

    private String type;

    private int blockIndex;

    private int sectionIndex;

    private ParagraphMeta paragraph;

    private TableMeta table;
}
