package com.sigma.ai.evaluation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送 HTML 邮件的请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendHtmlMailRequest {

    /** 收件人邮箱 */
    private String to;

    /** 邮件主题 */
    private String subject;

    /** 邮件 HTML 片段，服务端会包在固定 {@code html/head/body} 模板中并声明 UTF-8 */
    private String htmlBody;
}
