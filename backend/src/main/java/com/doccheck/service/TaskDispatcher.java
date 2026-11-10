package com.doccheck.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 队列消费者调度器。
 *
 * <p>队列长度不再固定为 3：必须先看校验线程池还有多少空闲槽位，
 * 否则 Redis 队列会持续把任务搬进内存线程池，进程崩溃时丢失范围扩大，
 * 也会让“排队中”的任务被提前标记为已领取。
 */
@Slf4j
@Component
public class TaskDispatcher {

    private static final int MAX_PER_TICK = 3;

    private final CheckQueueService queueService;
    private final CheckTaskWorker worker;
    private final ThreadPoolTaskExecutor checkExecutor;

    /**
     * 显式构造器保留 {@link Qualifier}，避免 Lombok 生成构造器时丢失限定符，
     * 在多线程池场景下注入到错误的 TaskExecutor。
     */
    public TaskDispatcher(CheckQueueService queueService,
                          CheckTaskWorker worker,
                          @Qualifier("checkExecutor") ThreadPoolTaskExecutor checkExecutor) {
        this.queueService = queueService;
        this.worker = worker;
        this.checkExecutor = checkExecutor;
    }

    @PostConstruct
    public void recover() {
        try {
            queueService.recoverProcessing();
            queueService.recoverOrphaned();
        } catch (Exception e) {
            log.warn("恢复队列失败（Redis 可能尚未就绪）", e);
        }
    }

    @Scheduled(fixedDelayString = "${check.queue.poll-interval-ms:1000}", initialDelayString = "2000")
    public void dispatch() {
        int slots = availableSlots();
        for (int i = 0; i < slots; i++) {
            Long taskId;
            try {
                taskId = queueService.dequeue();
            } catch (Exception e) {
                log.warn("领取任务失败: {}", e.getMessage());
                return;
            }
            if (taskId == null) {
                return;
            }
            worker.run(taskId);
        }
    }

    /**
     * 可领取数量 = min(剩余线程槽位, 队列剩余容量, 单 tick 上限)。
     *
     * <p>只看 activeCount 不够：队列容量耗尽时 CallerRunsPolicy 会让调度线程
     * 直接执行任务，所以还要限制队列剩余容量。
     */
    int availableSlots() {
        ThreadPoolExecutor pool = checkExecutor.getThreadPoolExecutor();
        if (pool == null || pool.isShutdown()) {
            return 0;
        }
        int freeThreads = Math.max(0, pool.getMaximumPoolSize() - pool.getActiveCount());
        int queueCapacity = pool.getQueue().remainingCapacity();
        int slots = Math.min(freeThreads, queueCapacity);
        return Math.max(0, Math.min(MAX_PER_TICK, slots));
    }
}
