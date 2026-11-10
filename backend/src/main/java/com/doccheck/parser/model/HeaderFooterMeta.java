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
public class HeaderFooterMeta {

    private int index;

    /** HEADER / FOOTER */
    private String type;

    /** 所属节（尽力映射，复杂分节文档可能为 0） */
    private int sectionIndex;

    /** default / first / even */
    private String headerFooterType;

    private String text;

    private boolean hasPageNumberField;

    private List<ParagraphMeta> paragraphs;
}
