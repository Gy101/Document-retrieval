package com.doccheck.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 校验任务专用线程池。队列满时由调用线程执行，避免任务被静默丢弃。
     */
    @Bean("checkExecutor")
    public ThreadPoolTaskExecutor checkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("doc-check-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 文档解析专用线程池。与 DSH 网络调用、任务编排隔离，避免大文档解析拖慢模型请求。
     */
    @Bean("parseExecutor")
    public ThreadPoolTaskExecutor parseExecutor(
            @Value("${check.parse.core:2}") int core,
            @Value("${check.parse.max:4}") int max,
            @Value("${check.parse.queue:100}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("doc-parse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * DSH 模型调用专用线程池。模型请求是长耗时网络 IO，不应占用解析线程或任务编排线程。
     */
    @Bean("dshExecutor")
    public ThreadPoolTaskExecutor dshExecutor(
            @Value("${check.dsh.core:2}") int core,
            @Value("${check.dsh.max:8}") int max,
            @Value("${check.dsh.queue:50}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("doc-dsh-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
