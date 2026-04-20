package com.sigma.ai.evaluation.trigger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitHub Webhook 验签等配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "github.webhook")
public class GithubWebhookProperties {

    /**
     * Webhook 密钥；与 GitHub 后台配置一致。为空时不校验签名（仅建议本地开发使用）。
     */
    private String secret = "";
}
