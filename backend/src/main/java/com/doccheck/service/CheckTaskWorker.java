package com.doccheck.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步执行入口。单独成 Bean，保证 @Async 代理生效（TaskService 内部自调用不会走代理）。
 */
@Component
@RequiredArgsConstructor
public class CheckTaskWorker {

    private final TaskService taskService;

    @Async("checkExecutor")
    public void run(Long taskId) {
        taskService.execute(taskId);
    }
}
