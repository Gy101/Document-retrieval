package com.doccheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doccheck.domain.dto.ProfileStatsVO;
import com.doccheck.domain.entity.CheckTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /** 同一 SHA-256 的历史任务文件可直接复用，避免重复落盘。 */
    @Select("""
            SELECT * FROM check_task
            WHERE doc_hash = #{hash} AND doc_path IS NOT NULL AND deleted = 0
            ORDER BY id DESC LIMIT 1
            """)
    CheckTask selectReusableFile(@Param("hash") String hash);

    /** 同一文件的历史解析结果可用于跳过再次解析；只取少量最新成功任务。 */
    @Select("""
            SELECT * FROM check_task
            WHERE doc_hash = #{hash} AND status = 'SUCCESS'
              AND document_metadata IS NOT NULL AND deleted = 0
            ORDER BY id DESC LIMIT 3
            """)
    List<CheckTask> selectMetadataCandidates(@Param("hash") String hash);
}
