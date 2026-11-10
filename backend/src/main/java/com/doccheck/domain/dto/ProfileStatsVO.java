package com.doccheck.domain.dto;

import lombok.Data;

/**
 * 个人中心统计。由 check_task 聚合查询填充。
 */
@Data
public class ProfileStatsVO {

    private long templateCount;
    private long taskCount;
    private long successTaskCount;
    private long diffCount;
    private long highCount;
}
