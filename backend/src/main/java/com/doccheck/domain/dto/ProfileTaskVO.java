package com.doccheck.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProfileTaskVO {

    private Long id;
    private String taskNo;
    private String templateName;
    private String docName;
    private String status;
    private Integer totalDiffCount;
    private Integer highCount;
    private LocalDateTime createdAt;
}
