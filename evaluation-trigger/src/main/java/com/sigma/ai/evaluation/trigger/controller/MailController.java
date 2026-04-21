package com.sigma.ai.evaluation.trigger.controller;

import com.sigma.ai.evaluation.api.controller.MailApi;
import com.sigma.ai.evaluation.api.dto.SendHtmlMailRequest;
import com.sigma.ai.evaluation.api.dto.SendHtmlMailResponse;
import com.sigma.ai.evaluation.domain.mail.adapter.HtmlMailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

/**
 * SMTP HTML 邮件 HTTP 入口（与 {@link HtmlMailPort} 一致，依赖 {@code spring.mail.host}）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mail.host")
public class MailController implements MailApi {

    private final HtmlMailPort htmlMailPort;

    @Override
    public ResponseEntity<SendHtmlMailResponse> sendHtmlMail(SendHtmlMailRequest request) {
        log.info("收到发送 HTML 邮件请求: to={}", request != null ? request.getTo() : null);
        if (request == null || !StringUtils.hasText(request.getTo())) {
            log.warn("发送 HTML 邮件请求参数非法: to 为空");
            return ResponseEntity.badRequest().body(SendHtmlMailResponse.builder()
                    .message("to 不能为空")
                    .build());
        }
        if (!StringUtils.hasText(request.getHtmlBody())) {
            log.warn("发送 HTML 邮件请求参数非法: htmlBody 为空, to={}", request.getTo());
            return ResponseEntity.badRequest().body(SendHtmlMailResponse.builder()
                    .message("htmlBody 不能为空")
                    .build());
        }
        htmlMailPort.sendHtmlMail(
                request.getTo().trim(),
                request.getSubject() != null ? request.getSubject() : "",
                request.getHtmlBody());
        log.info("发送 HTML 邮件请求处理完成: to={}", request.getTo());
        return ResponseEntity.ok(SendHtmlMailResponse.builder()
                .message("已发送")
                .build());
    }
}
