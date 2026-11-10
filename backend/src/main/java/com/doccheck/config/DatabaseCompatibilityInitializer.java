package com.doccheck.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 兼容已有数据库的轻量升级。
 *
 * <p>{@code schema.sql} 使用 CREATE TABLE IF NOT EXISTS，不会给已存在的
 * check_task 补新列；这里在应用启动后检查并补齐 retry_count。新库已经由
 * schema.sql 建好该列，因此这段逻辑是幂等的。DDL 失败只告警，不阻断启动；
 * 生产环境仍建议执行 db/upgrade-20261111.sql 并由 DBA 审核。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseCompatibilityInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer exists = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE()
                       AND table_name = 'check_task'
                       AND column_name = 'retry_count'
                    """, Integer.class);
            if (exists == null || exists == 0) {
                jdbcTemplate.execute("""
                        ALTER TABLE check_task
                          ADD COLUMN retry_count INT NOT NULL DEFAULT 0
                          COMMENT '已重试次数' AFTER progress
                        """);
                log.info("已为 check_task 补充 retry_count 列");
            }
        } catch (Exception e) {
            log.warn("检查/补充 check_task.retry_count 失败，请手工执行 db/upgrade-20261111.sql: {}", e.getMessage());
        }
    }
}
