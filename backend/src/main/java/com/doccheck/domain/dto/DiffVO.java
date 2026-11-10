package com.doccheck.domain.dto;

import lombok.Data;

@Data
public class DiffVO {

    private Long id;
    private String diffNo;
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
}
