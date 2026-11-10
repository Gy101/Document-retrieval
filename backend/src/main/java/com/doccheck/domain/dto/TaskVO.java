package com.doccheck.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskVO {

    private Long id;
    private String taskNo;
    private Long templateId;
    private String templateName;
    private String docName;
    private Long docSize;
    private String status;
    private Integer progress;
    private String stage;
    private String errorMessage;
    private String summary;
    private Integer totalDiffCount;
    private Integer highCount;
    private Integer middleCount;
    private Integer lowCount;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
