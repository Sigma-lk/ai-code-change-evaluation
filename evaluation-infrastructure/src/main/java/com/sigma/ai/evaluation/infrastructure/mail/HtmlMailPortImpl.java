package com.sigma.ai.evaluation.infrastructure.mail;

import com.sigma.ai.evaluation.domain.mail.adapter.HtmlMailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 基于 Spring {@link JavaMailSender} 的 HTML 邮件发送实现（163 等 SMTP）。
 *
 * <p>发送前将 {@code htmlBody} 包入固定 UTF-8 HTML 文档模板（含 {@code meta charset}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.mail.host")
public class HtmlMailPortImpl implements HtmlMailPort {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromUsername;

    /**
     * 将调用方传入的片段包在固定 HTML 文档壳中，并声明 UTF-8（{@code meta charset} + Content-Type）。
     */
    static String wrapAsUtf8HtmlDocument(String innerHtml) {
        String inner = innerHtml != null ? innerHtml : "";
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="UTF-8">
                <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
                </head>
                <body>
                """ + inner + """
                </body>
                </html>
                """;
    }

    @Override
    public void sendHtmlMail(String to, String subject, String htmlBody) {
        log.info("准备发送 HTML 邮件: to={}, subjectLen={}, bodyLen={}",
                to,
                subject != null ? subject.length() : 0,
                htmlBody != null ? htmlBody.length() : 0);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromUsername);
            helper.setTo(to);
            helper.setSubject(subject != null ? subject : "");
            helper.setText(wrapAsUtf8HtmlDocument(htmlBody), true);
            mailSender.send(message);
            log.info("HTML 邮件发送成功: to={}", to);
        } catch (MessagingException e) {
            log.error("发送 HTML 邮件失败: to={}, subject={}", to, subject, e);
            throw new IllegalStateException("发送邮件失败: " + e.getMessage(), e);
        }
    }
}
