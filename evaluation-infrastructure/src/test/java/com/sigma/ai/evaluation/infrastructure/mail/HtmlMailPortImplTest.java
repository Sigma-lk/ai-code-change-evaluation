package com.sigma.ai.evaluation.infrastructure.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HtmlMailPortImpl} 单元测试：验证会调用 {@link JavaMailSender#send}。
 */
@ExtendWith(MockitoExtension.class)
class HtmlMailPortImplTest {

    @Mock
    private JavaMailSender mailSender;

    private HtmlMailPortImpl htmlMailPort;

    @BeforeEach
    void setUp() {
        htmlMailPort = new HtmlMailPortImpl(mailSender);
        ReflectionTestUtils.setField(htmlMailPort, "fromUsername", "sender@163.com");
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendHtmlMail_delegatesToJavaMailSender() {
        htmlMailPort.sendHtmlMail("to@example.com", "主题", "<p>hi</p>");
        verify(mailSender).send(ArgumentMatchers.any(MimeMessage.class));
    }
}
