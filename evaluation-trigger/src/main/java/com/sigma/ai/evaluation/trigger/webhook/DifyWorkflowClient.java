package com.sigma.ai.evaluation.trigger.webhook;

import com.sigma.ai.evaluation.trigger.config.DifyWorkflowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用 Dify 工作流运行 API（blocking），将变更证据 JSON 作为输入变量传递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DifyWorkflowClient {

    private final RestTemplate restTemplate;
    private final DifyWorkflowProperties properties;

    /**
     * 以 blocking 模式触发工作流。
     *
     * @param changePayloadJson 供工作流消费的完整 JSON 字符串
     */
    public void runWorkflowBlocking(String changePayloadJson) {
        if (!properties.isEnabled()) {
            log.info("Dify 工作流未启用（dify.workflow.enabled=false），跳过调用");
            return;
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Dify workflow.api-key 未配置，跳过调用");
            return;
        }
        String base = properties.getBaseUrl().replaceAll("/+$", "");
        String url = base + "/v1/workflows/run";

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put(properties.getInputKey(), changePayloadJson);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        body.put("response_mode", "blocking");
        body.put("user", properties.getUser());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            log.info("Dify 工作流调用完成: httpStatus={}", resp.getStatusCode().value());
        } catch (Exception e) {
            log.error("Dify 工作流调用失败: url={}", url, e);
            throw e;
        }
    }
}
