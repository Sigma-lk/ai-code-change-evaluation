package com.sigma.ai.evaluation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 应用线程池统一配置。
 *
 * <p>各业务线程池通过 {@code @Qualifier} 按名称注入，所有参数均可在
 * {@code application.yml} 的 {@code thread-pool.*} 节点覆盖。
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    /**
     * 索引任务异步线程池，供 {@code IndexController} 等触发层异步提交全量/增量索引任务使用。
     *
     * <p>拒绝策略采用 {@link ThreadPoolExecutor.CallerRunsPolicy}：队列满时由调用方线程直接执行，
     * 起到自然背压的作用，避免任务丢失。
     *
     * @param coreSize         核心线程数
     * @param maxSize          最大线程数
     * @param queueCapacity    阻塞队列容量
     * @param keepAliveSeconds 空闲线程存活时间（秒）
     * @param threadNamePrefix 线程名前缀，便于排查日志
     * @return 已初始化的 {@link Executor}
     */
    @Bean("indexTaskExecutor")
    public Executor indexTaskExecutor(
            @Value("${thread-pool.index-task.core-size:4}") int coreSize,
            @Value("${thread-pool.index-task.max-size:8}") int maxSize,
            @Value("${thread-pool.index-task.queue-capacity:100}") int queueCapacity,
            @Value("${thread-pool.index-task.keep-alive-seconds:60}") int keepAliveSeconds,
            @Value("${thread-pool.index-task.thread-name-prefix:index-task-}") String threadNamePrefix) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(threadNamePrefix);
        // 队列满时由调用方线程执行，防止任务丢失并产生背压
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("索引任务线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}, prefix={}",
                coreSize, maxSize, queueCapacity, threadNamePrefix);
        return executor;
    }

    /**
     * GitHub Webhook 中 Dify 工作流投递专用线程池：与索引主流程解耦，避免 Dify {@code blocking} 长耗时阻塞 HTTP 响应。
     *
     * <p>拒绝策略采用 {@link ThreadPoolExecutor.CallerRunsPolicy}，队列满时由调用线程执行，避免静默丢任务。
     *
     * @param coreSize         核心线程数
     * @param maxSize          最大线程数
     * @param queueCapacity    阻塞队列容量
     * @param keepAliveSeconds 空闲线程存活时间（秒）
     * @param threadNamePrefix 线程名前缀
     * @return 已初始化的 {@link Executor}
     */
    @Bean("difyWorkflowExecutor")
    public Executor difyWorkflowExecutor(
            @Value("${thread-pool.dify-workflow.core-size:2}") int coreSize,
            @Value("${thread-pool.dify-workflow.max-size:4}") int maxSize,
            @Value("${thread-pool.dify-workflow.queue-capacity:100}") int queueCapacity,
            @Value("${thread-pool.dify-workflow.keep-alive-seconds:60}") int keepAliveSeconds,
            @Value("${thread-pool.dify-workflow.thread-name-prefix:dify-workflow-}") String threadNamePrefix) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        log.info("Dify 工作流投递线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}, prefix={}",
                coreSize, maxSize, queueCapacity, threadNamePrefix);
        return executor;
    }
}
