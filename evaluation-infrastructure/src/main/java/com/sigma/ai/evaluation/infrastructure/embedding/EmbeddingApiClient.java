package com.sigma.ai.evaluation.infrastructure.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sigma.ai.evaluation.types.ErrorCode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Embedding API 客户端，调用 OpenAI 兼容的 embeddings 接口获取向量。
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
        if (StringUtils.isNotBlank(properties.getApiKey())) {
            headers.setBearerAuth(properties.getApiKey());
        }

        List<float[]> allEmbeddings = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, properties.getBatchSize());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            List<String> batch = texts.subList(start, end);
            List<float[]> batchResult = requestBatch(headers, batch);
            if (batchResult == null || batchResult.size() != batch.size()) {
                log.warn("Embedding 批量向量数量不匹配，停止处理: expected={}, got={}",
                        batch.size(), batchResult == null ? 0 : batchResult.size());
                return null;
            }
            allEmbeddings.addAll(batchResult);
        }
        return allEmbeddings;
    }

    private List<float[]> requestBatch(HttpHeaders headers, List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "input", texts,
                "encoding_format", "float"
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                    properties.getUrl(), request, EmbeddingResponse.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null || response.getBody().getData() == null) {
                log.warn("[{}] Embedding API 返回异常状态: {}", ErrorCode.EMBEDDING_API_BAD_STATUS.getCode(), response.getStatusCode());
                return null;
            }
            return response.getBody().getData().stream()
                    .sorted(Comparator.comparingInt(EmbeddingItem::getIndex))
                    .map(EmbeddingItem::getEmbedding)
                    .toList();
        } catch (Exception e) {
            log.error("[{}] 调用 Embedding API 失败", ErrorCode.EMBEDDING_API_FAILED.getCode(), e);
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingResponse {
        private List<EmbeddingItem> data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingItem {
        @JsonProperty("embedding")
        private float[] embedding;

        @JsonProperty("index")
        private int index;
    }
}
