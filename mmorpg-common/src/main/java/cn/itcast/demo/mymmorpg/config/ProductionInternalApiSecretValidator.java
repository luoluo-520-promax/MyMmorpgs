package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 生产环境 internal-api 专用校验（与 {@link ProductionSecretsValidator} 互补）。
 * <p>
 * 策略：prod + {@code game.internal-api.enabled=true}（默认）时，
 * {@code INTERNAL_API_SECRET} 必须非空且非开发弱默认值。
 */
@Component
@ConditionalOnProperty(name = "game.internal-api.enabled", havingValue = "true", matchIfMissing = true)
public class ProductionInternalApiSecretValidator implements ApplicationListener<ApplicationReadyEvent> {

    private final InternalApiAuthProperties properties;
    private final Environment environment;

    public ProductionInternalApiSecretValidator(InternalApiAuthProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!isProdProfileActive()) {
            return;
        }
        String secret = properties.getSecret();
        if (!StringUtils.hasText(secret) || ProductionSecretsValidator.WEAK_SECRETS.contains(secret.trim())) {
            throw new IllegalStateException(
                    "生产环境必须配置非空的 INTERNAL_API_SECRET（game.internal-api.secret），"
                            + "且不得使用开发默认值（如 dev-internal-api-secret）。"
                            + " 开发可在 application-dev.yml 设置 game.internal-api.enabled=false；"
                            + " 生产请: export INTERNAL_API_SECRET='<随机强密钥>'");
        }
    }

    private boolean isProdProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }
}
