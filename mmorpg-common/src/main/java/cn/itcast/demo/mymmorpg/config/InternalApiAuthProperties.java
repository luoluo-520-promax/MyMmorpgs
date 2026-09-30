package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game.internal-api")
public class InternalApiAuthProperties {

    /** 是否启用内部 API 签名校验 */
    private boolean enabled = true;

    /** 服务间共享 HMAC 密钥，生产环境必须通过环境变量注入 */
    private String secret = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
