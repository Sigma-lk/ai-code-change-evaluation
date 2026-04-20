package com.sigma.ai.evaluation.trigger.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitHub Webhook HTTP 入口：仅处理 {@code push} 事件，完成同步增量索引、变更证据组装与 Dify 工作流投递。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class GithubWebhookController {

    private final GithubPushWebhookService githubPushWebhookService;

    /**
     * 接收 GitHub {@code push} 事件。
     *
     * @param rawBody         原始请求体字节（用于 HMAC-SHA256 验签，须与 GitHub 发送内容完全一致）
     * @param signature256    请求头 {@code X-Hub-Signature-256}，格式 {@code sha256=...}
     * @param githubEvent     请求头 {@code X-GitHub-Event}，仅 {@code push} 会执行索引与下游投递
     * @return 非 push 返回 204；签名校验失败 401；仓库 clone_url 未对齐 404；成功 200 且 body 为 {@link GithubWebhookAck}
     */
    @PostMapping(value = "/api/v1/webhooks/github", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GithubWebhookAck> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature256,
            @RequestHeader(value = "X-GitHub-Event", required = false) String githubEvent) {
        log.info("收到 GitHub Webhook: event={}, bytes={}", githubEvent, rawBody == null ? 0 : rawBody.length);
        return githubPushWebhookService.handlePush(rawBody, signature256, githubEvent);
    }
}
