package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * 生产环境启动时校验后台 HTTP 鉴权密钥。
 * <p>
 * prod + {@code admin.http.auth-enabled=true} 时：
 * {@code ADMIN_HMAC_SECRET} 必填且非弱默认；{@code ADMIN_API_KEY} 若配置也不得为弱默认。
 */
@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@EnableConfigurationProperties(AdminAiProperties.class)
public class AdminProductionSecretsValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Set<String> WEAK_SECRETS = Set.of(
            "",
            "dev-admin-hmac-secret",
            "dev-admin-api-key",
            "changeit",
            "123456");

    private final AdminHttpProperties properties;
    private final AdminAiProperties aiProperties;
    private final Environment environment;

    public AdminProductionSecretsValidator(AdminHttpProperties properties,
                                           AdminAiProperties aiProperties,
                                           Environment environment) {
        this.properties = properties;
        this.aiProperties = aiProperties;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!isProdProfileActive()) {
            return;
        }
        if (!properties.isAuthEnabled()) {
            throw new IllegalStateException(
                    "生产环境禁止关闭后台鉴权：请保持 admin.http.auth-enabled=true"
                            + "（环境变量 ADMIN_AUTH_ENABLED=true），并配置 ADMIN_HMAC_SECRET。"
                            + " 开发环境可在 application-dev.yml 将 auth-enabled 设为 false。");
        }
        String secret = properties.getHmacSecret();
        if (!StringUtils.hasText(secret) || WEAK_SECRETS.contains(secret.trim())) {
            throw new IllegalStateException(
                    "生产环境 admin-service 必须配置非空的 ADMIN_HMAC_SECRET（admin.http.hmac-secret），"
                            + "且不得使用开发默认值。"
                            + " 示例: export ADMIN_HMAC_SECRET='$(openssl rand -hex 32)'"
                            + " ; export ADMIN_API_KEY='$(openssl rand -hex 16)'"
                            + " ; export ADMIN_IP_WHITELIST_ENABLED=true");
        }
        String apiKey = properties.getApiKey();
        if (StringUtils.hasText(apiKey) && WEAK_SECRETS.contains(apiKey.trim())) {
            throw new IllegalStateException(
                    "生产环境 ADMIN_API_KEY 不得使用开发默认值（dev-admin-api-key）。"
                            + " 留空仅用 HMAC，或注入强随机 API Key。");
        }
        if (aiProperties.isEnabled()
                && (!StringUtils.hasText(aiProperties.getApiKey())
                || WEAK_SECRETS.contains(aiProperties.getApiKey().trim()))) {
            throw new IllegalStateException(
                    "生产环境启用 admin.ai.enabled 时必须配置非空 ADMIN_AI_API_KEY，"
                            + "且不得使用弱默认值。密钥仅通过环境变量注入，禁止写入仓库。");
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }
}
