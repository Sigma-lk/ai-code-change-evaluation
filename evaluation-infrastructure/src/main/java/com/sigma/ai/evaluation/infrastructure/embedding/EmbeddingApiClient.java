package com.sigma.ai.evaluation.infrastructure.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Embedding API 客户端，调用 Qwen / DeepSeek 的 text-embedding 接口获取向量。
 *
 * <p>接口格式参考通义千问 text-embedding-v3 规范。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingApiClient {

    private final EmbeddingApiProperties properties;
    private final RestTemplate restTemplate;

    /**
     * 对单条文本调用 Embedding API，返回浮点数向量。
     *
     * @param text 待嵌入的文本
     * @return 嵌入向量，失败时返回 null
     */
    public float[] embed(String text) {
        List<float[]> results = batchEmbed(List.of(text));
        return (results != null && !results.isEmpty()) ? results.get(0) : null;
    }

    /**
     * 批量调用 Embedding API。
     *
     * @param texts 待嵌入文本列表
     * @return 按输入顺序对应的向量列表，失败时返回 null
     */
    public List<float[]> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "input", Map.of("texts", texts),
                "parameters", Map.of("text_type", "document")
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                    properties.getUrl(), request, EmbeddingResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().getOutput().getEmbeddings().stream()
                        .map(EmbeddingItem::getEmbedding)
                        .toList();
            }
            log.warn("Embedding API 返回异常状态: {}", response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("调用 Embedding API 失败", e);
            return null;
        }
    }

    // -------- 响应 DTO --------

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingResponse {
        private Output output;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Output {
            private List<EmbeddingItem> embeddings;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingItem {
        @JsonProperty("embedding")
        private float[] embedding;
        @JsonProperty("text_index")
        private int textIndex;
    }
}
