package cn.itcast.demo.mymmorpg.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 内部微服务 HTTP API HMAC-SHA256 签名校验。
 * <p>签名原文：{@code timestamp + "\n" + method + "\n" + path + "\n" + playerId + "\n" + bodySha256Hex}</p>
 */
public final class InternalApiSignUtil {

    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private InternalApiSignUtil() {
    }

    public static String sign(String secret, long timestamp, String method, String path, long playerId, byte[] body) {
        String payload = buildPayload(timestamp, method, path, playerId, body);
        return hmacSha256Hex(secret, payload);
    }

    public static boolean verify(String secret, long timestamp, String method, String path, long playerId,
                                 byte[] body, String signature, long nowEpochMs) {
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        long skewMs = Math.abs(nowEpochMs - timestamp);
        if (skewMs > MAX_CLOCK_SKEW.toMillis()) {
            return false;
        }
        String expected = sign(secret, timestamp, method, path, playerId, body);
        return constantTimeEquals(expected, signature);
    }

    static String buildPayload(long timestamp, String method, String path, long playerId, byte[] body) {
        String bodyHash = sha256Hex(body == null ? new byte[0] : body);
        return timestamp + "\n" + method.toUpperCase() + "\n" + path + "\n" + playerId + "\n" + bodyHash;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
