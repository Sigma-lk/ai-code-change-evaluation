package com.sigma.ai.evaluation.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * 基础设施公共配置：RestTemplate、异步线程池等。
 */
@EnableAsync
@Configuration
public class InfrastructureConfig {

    /**
     * 供 EmbeddingApiClient 等通用 HTTP 调用使用的 RestTemplate。
     *
     * <p>标记为 {@code @Primary}，避免与 trigger 模块中专用于 Dify 长耗时 blocking 调用的
     * {@code difyWorkflowRestTemplate} 产生注入歧义。
     *
     * @return RestTemplate
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Embedding 异步写入线程池（供 {@code @Async("embeddingExecutor")} 使用）。
     * 核心线程数 4，最大 8，队列 500，防止大批量嵌入任务阻塞主流程。
     *
     * @return Executor
     */
    @Bean("embeddingExecutor")
    public Executor embeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("embedding-");
        executor.initialize();
        return executor;
    }
}
