package com.sigma.ai.evaluation.api.controller;

import com.sigma.ai.evaluation.api.dto.SendHtmlMailRequest;
import com.sigma.ai.evaluation.api.dto.SendHtmlMailResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 邮件发送 HTTP 契约（需配置 {@code spring.mail.host} 后实现类才会生效）。
 */
@RequestMapping("/api/v1")
public interface MailApi {

    /**
     * 通过已配置的 SMTP（如 163）发送 HTML 邮件。
     *
     * @param request 收件人、主题与 HTML 片段（仅 {@code body} 内内容即可，服务端统一包裹文档壳并指定 UTF-8）
     * @return 发送结果说明
     */
    @PostMapping("/mail/html")
    ResponseEntity<SendHtmlMailResponse> sendHtmlMail(@RequestBody SendHtmlMailRequest request);
}
