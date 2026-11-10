package com.doccheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doccheck.domain.dto.ProfileStatsVO;
import com.doccheck.domain.entity.CheckTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CheckTaskMapper extends BaseMapper<CheckTask> {

    /**
     * 一次聚合出个人中心的任务/差异统计，避免把用户全部历史任务加载进内存。
     */
    @Select("""
            SELECT COUNT(*) AS taskCount,
                   COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successTaskCount,
                   COALESCE(SUM(total_diff_count), 0) AS diffCount,
                   COALESCE(SUM(high_count), 0) AS highCount
            FROM check_task
            WHERE user_id = #{userId} AND deleted = 0
            """)
    ProfileStatsVO selectProfileStats(@Param("userId") Long userId);
}
