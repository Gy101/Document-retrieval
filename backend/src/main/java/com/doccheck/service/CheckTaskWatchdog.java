package com.doccheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.doccheck.domain.entity.CheckTask;
import com.doccheck.domain.enums.TaskStatus;
import com.doccheck.mapper.CheckTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RUNNING 任务超时看门狗。
 *
 * <p>只处理数据库 updated_at 超过阈值的任务。UPDATE 带 status=RUNNING 与
 * updated_at 条件，避免把看门狗扫描后刚好完成的任务重新标记为失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckTaskWatchdog {

    private final CheckTaskMapper taskMapper;
    private final CheckQueueService queueService;

    @Value("${check.watchdog.timeout-minutes:15}")
    private long timeoutMinutes;

    @Value("${check.watchdog.batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${check.watchdog.interval-ms:30000}", initialDelayString = "30000")
    public void scan() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        List<CheckTask> stale;
        try {
            stale = taskMapper.selectList(Wrappers.<CheckTask>lambdaQuery()
                    .eq(CheckTask::getStatus, TaskStatus.RUNNING.name())
                    .lt(CheckTask::getUpdatedAt, cutoff)
                    .last("LIMIT " + Math.max(1, Math.min(200, batchSize))));
        } catch (Exception e) {
            log.warn("看门狗扫描失败: {}", e.getMessage());
            return;
        }
        for (CheckTask task : stale) {
            String message = "任务执行超过 " + timeoutMinutes + " 分钟，已由看门狗终止";
            CheckTask patch = new CheckTask();
            patch.setStatus(TaskStatus.FAILED.name());
            patch.setProgress(100);
            patch.setStage("执行超时");
            patch.setErrorMessage(message);
            patch.setFinishedAt(LocalDateTime.now());
            patch.setUpdatedAt(LocalDateTime.now());
            int updated = taskMapper.update(patch, Wrappers.<CheckTask>lambdaUpdate()
                    .eq(CheckTask::getId, task.getId())
                    .eq(CheckTask::getStatus, TaskStatus.RUNNING.name())
                    .lt(CheckTask::getUpdatedAt, cutoff));
            if (updated > 0) {
                queueService.markStatus(task.getId(), TaskStatus.FAILED.name(), 100, "执行超时");
                queueService.ack(task.getId());
                log.warn("任务 {} 执行超时，已标记 FAILED", task.getId());
            }
        }
    }
}
