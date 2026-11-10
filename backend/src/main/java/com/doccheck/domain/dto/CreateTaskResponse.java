package com.doccheck.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateTaskResponse {

    private Long taskId;
    private String taskNo;
    private String status;
    private Integer progress;
}
