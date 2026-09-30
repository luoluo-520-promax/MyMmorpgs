package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.security.InternalApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.InternalApiSignUtil;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GuildFeignConfiguration {

    @Bean
    RequestInterceptor guildInternalApiAuthRequestInterceptor(InternalApiAuthProperties properties) {
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
                    properties.getSecret(), timestamp, template.method(), path, playerId, body);
            template.header(InternalApiAuthHeaders.TIMESTAMP, Long.toString(timestamp));
            template.header(InternalApiAuthHeaders.SIGNATURE, signature);
        };
    }
}
