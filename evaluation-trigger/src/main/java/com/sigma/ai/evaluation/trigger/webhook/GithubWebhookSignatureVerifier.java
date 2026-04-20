package com.sigma.ai.evaluation.trigger.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 校验 GitHub {@code X-Hub-Signature-256}（HMAC-SHA256）。
 */
public final class GithubWebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";

    private GithubWebhookSignatureVerifier() {
    }

    /**
     * @param secret        Webhook secret
     * @param payloadBody   原始请求体字节
     * @param signature256  Header {@code X-Hub-Signature-256} 值
     * @return 是否校验通过
     */
    public static boolean isValid(String secret, byte[] payloadBody, String signature256) {
        if (secret == null || secret.isEmpty() || signature256 == null || !signature256.startsWith(PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(payloadBody);
            String expected = PREFIX + HexFormat.of().formatHex(sig);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature256.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }
}
