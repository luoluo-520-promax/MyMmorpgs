package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "game.session-crypto")
public class SessionCryptoProperties {

    /** Base64 PKCS#8 私钥；未配置时启动生成（重启后客户端需重新协商） */
    private String rsaPrivateKeyBase64 = "";

    /** Base64 X.509 公钥；与私钥成对配置 */
    private String rsaPublicKeyBase64 = "";

    /** 会话 AES 密钥 Redis TTL */
    private Duration sessionTtl = Duration.ofHours(24);

    public String getRsaPrivateKeyBase64() {
        return rsaPrivateKeyBase64;
    }

    public void setRsaPrivateKeyBase64(String rsaPrivateKeyBase64) {
        this.rsaPrivateKeyBase64 = rsaPrivateKeyBase64;
    }

    public String getRsaPublicKeyBase64() {
        return rsaPublicKeyBase64;
    }

    public void setRsaPublicKeyBase64(String rsaPublicKeyBase64) {
        this.rsaPublicKeyBase64 = rsaPublicKeyBase64;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }
}
