package com.doccheck.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("check_diff")
public class CheckDiff {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

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

    private LocalDateTime createdAt;
}
