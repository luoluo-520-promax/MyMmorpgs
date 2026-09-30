package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.security.InternalApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.InternalApiSignUtil;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * shop-service Feign 调用 bag/activity 内部 API 时附加 HMAC 签名。
 */
@Configuration
public class ShopInternalApiFeignConfiguration {

    @Bean
    RequestInterceptor shopInternalApiAuthRequestInterceptor(InternalApiAuthProperties properties) {
        return template -> {
            if (!properties.isEnabled() || properties.getSecret() == null || properties.getSecret().isBlank()) {
                return;
            }
            String path = template.path();
            if (path == null || !path.startsWith("/internal/")) {
                return;
            }
            long playerId = 0L;
            if (template.headers().containsKey(InternalApiAuthHeaders.PLAYER_ID)) {
                String raw = template.headers().get(InternalApiAuthHeaders.PLAYER_ID).iterator().next();
                playerId = Long.parseLong(raw);
            }
            byte[] body = template.body() == null ? new byte[0] : template.body();
            long timestamp = System.currentTimeMillis();
            String signature = InternalApiSignUtil.sign(
                    properties.getSecret(),
                    timestamp,
                    template.method(),
                    path,
                    playerId,
                    body);
            template.header(InternalApiAuthHeaders.TIMESTAMP, Long.toString(timestamp));
            template.header(InternalApiAuthHeaders.SIGNATURE, signature);
        };
    }
}
