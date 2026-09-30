package cn.itcast.demo.mymmorpg.model.update;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Manifest HMAC-SHA256 签名：服务端私钥签名，客户端用共享密钥验签。
 */
public final class ManifestSignatureUtil {

    private ManifestSignatureUtil() {
    }

    public static String signHmacSha256(String payload, String secret) {
        if (payload == null || secret == null || secret.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))).toLowerCase();
        } catch (Exception e) {
            throw new IllegalStateException("manifest sign failed", e);
        }
    }

    public static boolean verify(String payload, String secret, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = signHmacSha256(payload, secret);
        return ManifestChecksumUtil.matches(expected, signature);
    }
}
