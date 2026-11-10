package com.doccheck.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NumberingMeta {

    /** 原始 numId（与 numbering.xml 对应） */
    private Long numId;

    /** 0 表示一级编号 */
    private Integer ilvl;

    /** decimal / lowerLetter / bullet / chineseCounting 等 */
    private String numFmt;

    /** 例如 "%1."、"（%1）"、"•" */
    private String levelText;

    private Long start;

    private String suffix;

    /** 由 Word 计算出的编号文本（部分文档可能为空） */
    private String label;
}
