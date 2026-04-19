package com.sigma.ai.evaluation.infrastructure.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 服务配置属性，读取 application.yml 中 {@code embedding} 前缀的配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingApiProperties {

    /** Embedding 服务端点 URL，如 http://localhost:8081/v1/embeddings */
    private String url;

    /** API Key，本地部署时可为空 */
    private String apiKey;

    /** 模型名称，如 BAAI/bge-m3 */
    private String model = "BAAI/bge-m3";

    /** 单次批量请求最大文本数 */
    private int batchSize = 25;

    /** 向量维度 */
    private int dimension = 1024;
}
