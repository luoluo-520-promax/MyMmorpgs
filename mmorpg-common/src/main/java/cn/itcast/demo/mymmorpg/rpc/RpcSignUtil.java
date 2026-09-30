package cn.itcast.demo.mymmorpg.rpc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 跨服 RPC 握手签名工具：HMAC-SHA256(serverId, signKey) 十六进制小写。
 */
public final class RpcSignUtil {

    private RpcSignUtil() {
    }

    public static String sign(int serverId, String signKey) {
        if (signKey == null || signKey.isBlank()) {
            throw new IllegalArgumentException("signKey must not be blank");
        }
        return hmacSha256Hex(signKey, Integer.toString(serverId));
    }

    public static boolean verify(String signKey, int serverId, String sign) {
        if (sign == null || signKey == null || signKey.isBlank()) {
            return false;
        }
        return constantTimeEquals(sign(serverId, signKey), sign);
    }

    private static String hmacSha256Hex(String signKey, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
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
