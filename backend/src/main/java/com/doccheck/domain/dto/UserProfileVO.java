package com.doccheck.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserProfileVO {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
    private ProfileStatsVO stats;
    private List<ProfileTaskVO> recentTasks;
}
