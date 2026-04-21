package com.sigma.ai.evaluation.domain.mail.adapter;

/**
 * HTML 邮件发送端口（SMTP，如 163：{@code smtp.163.com} + 授权码）。
 *
 * <p>由基础设施模块基于 {@code JavaMailSender} 实现；未配置 {@code spring.mail.host} 时不装配实现类。
 */
public interface HtmlMailPort {

    /**
     * 发送单收件人、HTML 正文的邮件（UTF-8）。
     *
     * @param to       收件人邮箱地址
     * @param subject  邮件主题
     * @param htmlBody 将置于固定 HTML 模板 {@code <body>} 内的片段（无需自带 {@code html/head}；UTF-8 由模板与 MIME 声明）
     * @throws RuntimeException 封装底层发送失败（如 {@code MessagingException}）
     */
    void sendHtmlMail(String to, String subject, String htmlBody);
}
