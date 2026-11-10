package com.doccheck.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 归一化后的格式差异。DSH 返回值与本地规则结果都先转换成该对象，再统一去重、排序、落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormatDiff {

    private String diffId;
    /** 差异在本次校验结果中的展示序号（从 1 开始）。 */
    private Integer seq;
    private String location;
    private String anchorPath;
    private Integer sectionIndex;
    private Integer paragraphIndex;
    private Integer tableIndex;
    private Integer rowIndex;
    private Integer colIndex;
    private Integer charStart;
    private Integer charEnd;
    private String diffType;
    private String severity;
    private String elementType;
    private String templateFormat;
    private String actualFormat;
    private String contentSnippet;
    private String suggestion;

    /** 用于去重的业务键：同一位置、同一类型、同一期望/实际值只保留一条。 */
    public String dedupeKey() {
        return (anchorPath == null ? "" : anchorPath) + "|" + (diffType == null ? "" : diffType) + "|"
                + (templateFormat == null ? "" : templateFormat) + "|" + (actualFormat == null ? "" : actualFormat);
    }
}
