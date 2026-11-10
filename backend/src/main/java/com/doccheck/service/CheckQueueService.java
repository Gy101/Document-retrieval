package com.doccheck.service;

import com.doccheck.domain.entity.CheckTask;
import com.doccheck.domain.enums.TaskStatus;
import com.doccheck.mapper.CheckTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    public static final String DEAD_KEY = "doccheck:queue:dead";
    public static final String PROGRESS_KEY_PREFIX = "doccheck:task:progress:";
    public static final String INSTANCE_ZSET = "doccheck:instances";
    public static final String INSTANCE_KEY_PREFIX = "doccheck:instance:";
    public static final String PROCESSING_KEY_PREFIX = "doccheck:queue:processing:";
    private static final Duration INSTANCE_TTL = Duration.ofSeconds(90);
    private static final long ORPHAN_AFTER_MS = 90_000L;

    private final StringRedisTemplate redisTemplate;
    private final CheckTaskMapper taskMapper;

    @Value("${check.instance-id:${INSTANCE_ID:}}")
    private String configuredInstanceId;

    private volatile String instanceId;

    @PostConstruct
    public void initInstance() {
        String id = configuredInstanceId;
        if (id == null || id.isBlank()) {
            String host;
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "local";
            }
            id = host + "-" + ManagementFactory.getRuntimeMXBean().getName() + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
        }
        this.instanceId = id.replaceAll("[^a-zA-Z0-9._-]", "_");
        registerInstance();
        log.info("队列实例标识: {}", this.instanceId);
    }

    public String instanceId() {
        if (instanceId == null) {
            initInstance();
        }
        return instanceId;
    }

    private String instanceKey(String id) {
        return INSTANCE_KEY_PREFIX + id;
    }

    private String processingKey() {
        return PROCESSING_KEY_PREFIX + instanceId();
    }

    private String processingKey(String id) {
        return PROCESSING_KEY_PREFIX + id;
    }

    /** 注册当前实例并续租；实例列表用于发现已崩溃实例遗留的 processing 队列。 */
    public void registerInstance() {
        try {
            String id = instanceId();
            redisTemplate.opsForValue().set(instanceKey(id), String.valueOf(System.currentTimeMillis()),
                    INSTANCE_TTL);
            redisTemplate.opsForZSet().add(INSTANCE_ZSET, id, System.currentTimeMillis());
        } catch (RuntimeException e) {
            log.warn("队列实例注册失败: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${check.queue.heartbeat-ms:30000}", initialDelayString = "5000")
    public void heartbeat() {
        registerInstance();
        recoverOrphaned();
    }

    public void enqueue(Long taskId) {
        redisTemplate.opsForList().leftPush(QUEUE_KEY, String.valueOf(taskId));
        updateProgress(taskId, 0, "排队中");
    }

    /** 原子取出一个任务；没有任务时返回 null。 */
    public Long dequeue() {
        String value = redisTemplate.opsForList().rightPopAndLeftPush(QUEUE_KEY, processingKey());
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("队列中存在非法任务 ID: {}", value);
            redisTemplate.opsForList().remove(processingKey(), 1, value);
            return null;
        }
    }

    public void ack(Long taskId) {
        redisTemplate.opsForList().remove(processingKey(), 1, String.valueOf(taskId));
    }

    /** 无法重试的任务写入死信列表，保留现场供人工排查；最多保留 1000 条。 */
    public void deadLetter(Long taskId, String reason) {
        String payload = taskId + "|" + System.currentTimeMillis() + "|"
                + (reason == null ? "" : reason.replace('\n', ' '));
        redisTemplate.opsForList().leftPush(DEAD_KEY, payload);
        redisTemplate.opsForList().trim(DEAD_KEY, 0, 999);
    }

    public Long deadLetterSize() {
        return redisTemplate.opsForList().size(DEAD_KEY);
    }

    /** 启动时恢复当前实例的 processing 列表；其他实例的遗留队列由 recoverOrphaned 处理。 */
    public void recoverProcessing() {
        recoverList(processingKey());
    }

    /**
     * 发现心跳已过期的其它实例，回收其 processing 列表。
     *
     * <p>用每实例独立的 processing key 替代单一全局列表：正常实例只 ack 自己的任务，
     * 不会把其他实例正在执行的任务 LREM 掉；实例崩溃后由租约过期触发回收。
     */
    public void recoverOrphaned() {
        long cutoff = System.currentTimeMillis() - ORPHAN_AFTER_MS;
        Set<String> ids;
        try {
            ids = redisTemplate.opsForZSet().rangeByScore(INSTANCE_ZSET, 0, cutoff);
        } catch (RuntimeException e) {
            log.warn("扫描遗留实例失败: {}", e.getMessage());
            return;
        }
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            if (id == null || id.equals(instanceId())) {
                continue;
            }
            try {
                if (Boolean.TRUE.equals(redisTemplate.hasKey(instanceKey(id)))) {
                    redisTemplate.opsForZSet().add(INSTANCE_ZSET, id, System.currentTimeMillis());
                    continue;
                }
                String lockKey = "doccheck:lock:recover:" + id;
                Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(60));
                if (!Boolean.TRUE.equals(locked)) {
                    continue;
                }
                try {
                    recoverList(processingKey(id));
                    redisTemplate.delete(instanceKey(id));
                    redisTemplate.opsForZSet().remove(INSTANCE_ZSET, id);
                } finally {
                    redisTemplate.delete(lockKey);
                }
            } catch (RuntimeException e) {
                log.warn("回收实例 {} 的遗留任务失败: {}", id, e.getMessage());
            }
        }
    }

    private void recoverList(String processingKey) {
        List<String> values = redisTemplate.opsForList().range(processingKey, 0, -1);
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> distinct = new LinkedHashSet<>(values);
        for (String value : distinct) {
            Long taskId;
            try {
                taskId = Long.parseLong(value);
            } catch (NumberFormatException e) {
                redisTemplate.opsForList().remove(processingKey, 1, value);
                continue;
            }
            CheckTask task = taskMapper.selectById(taskId);
            if (task == null || TaskStatus.isTerminal(task.getStatus())) {
                redisTemplate.opsForList().remove(processingKey, 1, value);
                continue;
            }
            CheckTask patch = new CheckTask();
            patch.setId(taskId);
            patch.setStatus(TaskStatus.QUEUED.name());
            patch.setStage("排队中（实例恢复）");
            patch.setUpdatedAt(java.time.LocalDateTime.now());
            taskMapper.updateById(patch);
            redisTemplate.opsForList().leftPush(QUEUE_KEY, value);
            redisTemplate.opsForList().remove(processingKey, 1, value);
            log.info("恢复排队任务 {}（processing={}）", taskId, processingKey);
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

    /** 仅供运维/测试：当前实例 processing 长度。 */
    public Long processingSize() {
        return redisTemplate.opsForList().size(processingKey());
    }
}
