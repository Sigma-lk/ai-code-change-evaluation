package com.sigma.ai.evaluation.infrastructure.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding API 配置属性，读取 application.yml 中 {@code embedding} 前缀的配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingApiProperties {

    /** Embedding API 端点 URL，如 https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding */
    private String url;

    /** API Key */
    private String apiKey;

    /** 模型名称，如 text-embedding-v3 */
    private String model = "text-embedding-v3";

    /** 单次批量请求最大文本数 */
    private int batchSize = 25;

    /** 向量维度 */
    private int dimension = 1536;
}
