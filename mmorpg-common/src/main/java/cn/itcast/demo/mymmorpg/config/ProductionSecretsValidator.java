package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 生产环境启动时统一校验关键密钥：禁止空值与弱默认值。
 * <p>
 * 环境变量矩阵见仓库根目录 {@code .env.example} / {@code DEPLOYMENT.md}。
 */
@Component
public class ProductionSecretsValidator {

    static final Set<String> WEAK_SECRETS = Set.of(
            "",
            "123456",
            "changeit",
            "5eb4bfea5d024e1d92c19cb6e4baab7d",
            "dev-internal-api-secret",
            "dev-rpc-sign-key",
            "dev-admin-hmac-secret",
            "dev-admin-api-key");

    private final Environment environment;

    public ProductionSecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateOnStartup() {
        if (!isProdProfile()) {
            return;
        }
        List<String> missing = new ArrayList<>();

        if (hasDatasource()) {
            check(missing, "MYSQL_PASSWORD", environment.getProperty("spring.datasource.password"));
        }
        if (environment.containsProperty("rpc.signKey")
                || StringUtils.hasText(environment.getProperty("rpc.signKey"))) {
            check(missing, "RPC_SIGN_KEY", environment.getProperty("rpc.signKey"));
        }
        if (isInternalApiEnabled()) {
            check(missing, "INTERNAL_API_SECRET", environment.getProperty("game.internal-api.secret"));
        }
        if (isSslEnabled()) {
            check(missing, "GATEWAY_SSL_KEY_STORE_PASSWORD / SERVER_SSL_KEY_STORE_PASSWORD",
                    firstNonBlank(
                            environment.getProperty("server.ssl.key-store-password"),
                            environment.getProperty("GATEWAY_SSL_KEY_STORE_PASSWORD")));
        }
        if (isSessionCryptoServicePresent()) {
            check(missing, "SESSION_RSA_PRIVATE_KEY",
                    environment.getProperty("game.session-crypto.rsa-private-key-base64"));
            check(missing, "SESSION_RSA_PUBLIC_KEY",
                    environment.getProperty("game.session-crypto.rsa-public-key-base64"));
            if (!hasRedisConfigured()) {
                missing.add("REDIS_HOST (会话 AES 密钥必须走 Redis，禁止进程内存)");
            }
        }
        if (isShopPaymentProductionVerify()) {
            if (Boolean.parseBoolean(environment.getProperty("shop.payment.mock-enabled", "false"))) {
                missing.add("shop.payment.mock-enabled 必须为 false（生产禁止 MOCK/DEV 渠道）");
            }
            if (!hasAnyShopChannelSecret()) {
                missing.add("SHOP_PAYMENT_CHANNEL_SECRET_WECHAT|ALIPAY|APP_STORE|GOOGLE_PLAY"
                        + "（生产至少配置一个非 Stub 沙箱/真实渠道密钥）");
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "生产环境缺少或未正确配置以下密钥（不可为空或使用弱默认值）: "
                            + String.join(", ", missing)
                            + "。请通过环境变量注入，参考仓库根目录 .env.example。"
                            + " 示例: export INTERNAL_API_SECRET='<随机强密钥>'");
        }
    }

    private static void check(List<String> missing, String name, String value) {
        if (value == null || WEAK_SECRETS.contains(value.trim())) {
            missing.add(name);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        return b;
    }

    private boolean isProdProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInternalApiEnabled() {
        return Boolean.parseBoolean(environment.getProperty("game.internal-api.enabled", "true"));
    }

    private boolean hasDatasource() {
        return StringUtils.hasText(environment.getProperty("spring.datasource.url"));
    }

    private boolean isSslEnabled() {
        return Boolean.parseBoolean(environment.getProperty("server.ssl.enabled", "false"));
    }

    /** player-service 等注册了 session-crypto 属性时强制固定 RSA + Redis。 */
    private boolean isSessionCryptoServicePresent() {
        return environment.containsProperty("game.session-crypto.session-ttl")
                || environment.containsProperty("game.session-crypto.rsa-private-key-base64")
                || StringUtils.hasText(environment.getProperty("game.session-crypto.rsa-private-key-base64"))
                || "player-service".equals(environment.getProperty("spring.application.name"));
    }

    private boolean hasRedisConfigured() {
        return StringUtils.hasText(environment.getProperty("spring.data.redis.host"))
                || StringUtils.hasText(environment.getProperty("REDIS_HOST"));
    }

    private boolean isShopPaymentProductionVerify() {
        String app = environment.getProperty("spring.application.name", "");
        boolean shopApp = "shop-service".equals(app) || "player-service".equals(app);
        if (!shopApp) {
            return false;
        }
        return Boolean.parseBoolean(environment.getProperty("shop.payment.production-verify-enabled", "false"));
    }

    private boolean hasAnyShopChannelSecret() {
        return StringUtils.hasText(environment.getProperty("shop.payment.channel-secrets.wechat"))
                || StringUtils.hasText(environment.getProperty("shop.payment.channel-secrets.alipay"))
                || StringUtils.hasText(environment.getProperty("shop.payment.channel-secrets.app-store"))
                || StringUtils.hasText(environment.getProperty("shop.payment.channel-secrets.google-play"));
    }
}
