package com.doccheck.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.doccheck.domain.entity.CheckTask;
import com.doccheck.domain.enums.TaskStatus;
import com.doccheck.mapper.CheckTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Redis List 的可靠队列。
 *
 * <p>入队：LPUSH doccheck:queue
 * <p>消费：RPOPLPUSH doccheck:queue doccheck:queue:processing（原子迁移，进程崩溃不丢任务）
 * <p>确认：业务结束后从 processing 中 LREM
 * <p>恢复：启动时把 processing 中未完成的任务重新放回主队列
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckQueueService {

    public static final String QUEUE_KEY = "doccheck:queue";
    public static final String PROCESSING_KEY = "doccheck:queue:processing";
    public static final String PROGRESS_KEY_PREFIX = "doccheck:task:progress:";

    private final StringRedisTemplate redisTemplate;
    private final CheckTaskMapper taskMapper;

    public void enqueue(Long taskId) {
        redisTemplate.opsForList().leftPush(QUEUE_KEY, String.valueOf(taskId));
        updateProgress(taskId, 0, "排队中");
    }

    /** 原子取出一个任务；没有任务时返回 null。 */
    public Long dequeue() {
        String value = redisTemplate.opsForList().rightPopAndLeftPush(QUEUE_KEY, PROCESSING_KEY);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("队列中存在非法任务 ID: {}", value);
            redisTemplate.opsForList().remove(PROCESSING_KEY, 1, value);
            return null;
        }
    }

    public void ack(Long taskId) {
        redisTemplate.opsForList().remove(PROCESSING_KEY, 1, String.valueOf(taskId));
    }

    /** 启动时恢复 processing 列表，避免进程崩溃后任务永久停留在「处理中」。 */
    public void recoverProcessing() {
        List<String> values = redisTemplate.opsForList().range(PROCESSING_KEY, 0, -1);
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> distinct = new LinkedHashSet<>(values);
        for (String value : distinct) {
            Long taskId;
            try {
                taskId = Long.parseLong(value);
            } catch (NumberFormatException e) {
                redisTemplate.opsForList().remove(PROCESSING_KEY, 1, value);
                continue;
            }
            CheckTask task = taskMapper.selectById(taskId);
            if (task == null || TaskStatus.isTerminal(task.getStatus())) {
                redisTemplate.opsForList().remove(PROCESSING_KEY, 1, value);
                continue;
            }
            CheckTask patch = new CheckTask();
            patch.setId(taskId);
            patch.setStatus(TaskStatus.QUEUED.name());
            patch.setStage("排队中（服务重启恢复）");
            patch.setUpdatedAt(java.time.LocalDateTime.now());
            taskMapper.updateById(patch);
            redisTemplate.opsForList().leftPush(QUEUE_KEY, value);
            redisTemplate.opsForList().remove(PROCESSING_KEY, 1, value);
            log.info("恢复排队任务 {}", taskId);
        }
    }

    /** 进度同时写 Redis（供轮询快速读取）与数据库（供历史查询）。 */
    public void updateProgress(Long taskId, int progress, String stage) {
        String key = PROGRESS_KEY_PREFIX + taskId;
        redisTemplate.opsForHash().putAll(key, Map.of(
                "progress", String.valueOf(progress),
                "stage", stage == null ? "" : stage,
                "updatedAt", String.valueOf(System.currentTimeMillis())));
        redisTemplate.expire(key, Duration.ofDays(2));
    }

    public Map<Object, Object> getProgress(Long taskId) {
        return redisTemplate.opsForHash().entries(PROGRESS_KEY_PREFIX + taskId);
    }

    public void markStatus(Long taskId, String status, int progress, String stage) {
        String key = PROGRESS_KEY_PREFIX + taskId;
        redisTemplate.opsForHash().putAll(key, Map.of(
                "status", status,
                "progress", String.valueOf(progress),
                "stage", stage == null ? "" : stage,
                "updatedAt", String.valueOf(System.currentTimeMillis())));
        redisTemplate.expire(key, Duration.ofDays(2));
    }

    /** 仅供运维/测试：查询当前排队长度。 */
    public Long queueSize() {
        return redisTemplate.opsForList().size(QUEUE_KEY);
    }
}
