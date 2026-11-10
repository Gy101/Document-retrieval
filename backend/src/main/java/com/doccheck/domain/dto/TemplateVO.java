package com.doccheck.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateVO {

    private Long id;
    private String name;
    private String description;
    private String category;
    private String scope;
    private Long ownerId;
    private String originalName;
    private Long fileSize;
    private String parseStatus;
    private String parseMessage;
    private Integer metadataVersion;
    private Integer downloadCount;
    private LocalDateTime createdAt;
}
