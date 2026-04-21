package com.sigma.ai.evaluation.trigger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Dify 工作流 HTTP 客户端：blocking 模式可能长时间无响应体返回，使用独立 {@link RestTemplate} 并配置较长读超时。
 */
@Configuration
public class DifyWorkflowHttpConfig {

    /**
     * 专用于 {@link com.sigma.ai.evaluation.trigger.webhook.DifyWorkflowClient}，避免与通用 {@link RestTemplate} 混用超时策略。
     *
     * @param properties Dify 工作流配置（含读/连接超时）
     * @return 已配置超时的 RestTemplate
     */
    @Bean("difyWorkflowRestTemplate")
    public RestTemplate difyWorkflowRestTemplate(DifyWorkflowProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(safeIntMs(properties.getConnectTimeoutMs(), "connectTimeoutMs"));
        factory.setReadTimeout(safeIntMs(properties.getReadTimeoutMs(), "readTimeoutMs"));
        return new RestTemplate(factory);
    }

    private static int safeIntMs(long ms, String name) {
        if (ms <= 0) {
            throw new IllegalArgumentException("dify.workflow." + name + " 必须为正数（毫秒）");
        }
        if (ms > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) ms;
    }
}
