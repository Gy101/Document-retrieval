package com.doccheck.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 队列消费者调度器：每次最多领取 3 个任务交给校验线程池，避免单次 tick 抢占过多。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatcher {

    private final CheckQueueService queueService;
    private final CheckTaskWorker worker;

    @PostConstruct
    public void recover() {
        try {
            queueService.recoverProcessing();
        } catch (Exception e) {
            log.warn("恢复队列失败（Redis 可能尚未就绪）", e);
        }
    }

    @Scheduled(fixedDelayString = "${check.queue.poll-interval-ms:1000}", initialDelayString = "2000")
    public void dispatch() {
        for (int i = 0; i < 3; i++) {
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
}
