package com.sigma.ai.evaluation.infrastructure.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HtmlMailPortImpl#wrapAsUtf8HtmlDocument} 纯函数测试（不依赖 {@link org.springframework.mail.javamail.JavaMailSender}）。
 */
class HtmlMailPortImplWrapAsUtf8DocumentTest {

    @Test
    void nullBecomesEmptyBody() {
        String out = HtmlMailPortImpl.wrapAsUtf8HtmlDocument(null);
        assertTrue(out.contains("<body>"));
        assertTrue(out.contains("</body>"));
        assertTrue(out.contains("<meta charset=\"UTF-8\">"));
        assertTrue(out.contains("http-equiv=\"Content-Type\""));
    }

    @Test
    void embedsFragmentInsideBody() {
        String out = HtmlMailPortImpl.wrapAsUtf8HtmlDocument("<p>hi</p>");
        assertTrue(out.contains("<p>hi</p>"));
        assertTrue(out.indexOf("<body>") < out.indexOf("<p>hi</p>"));
        assertTrue(out.indexOf("<p>hi</p>") < out.indexOf("</body>"));
    }
}
