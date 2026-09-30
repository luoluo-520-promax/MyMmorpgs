package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.security.InternalApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.InternalApiSignUtil;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * admin-service 通过 Feign 调用各域 /internal/** 导入接口时附加 HMAC 签名。
 */
@Configuration
public class AdminFeignConfiguration {

    @Bean
    RequestInterceptor adminInternalApiAuthInterceptor(InternalApiAuthProperties properties) {
        return template -> {
            if (!properties.isEnabled() || properties.getSecret() == null || properties.getSecret().isBlank()) {
                return;
            }
            String path = template.path();
            if (path == null || !path.startsWith("/internal/")) {
                return;
            }
            byte[] body = template.body() == null ? new byte[0] : template.body();
            long timestamp = System.currentTimeMillis();
            String signature = InternalApiSignUtil.sign(
                    properties.getSecret(),
                    timestamp,
                    template.method(),
                    path,
                    0L,
                    body);
            template.header(InternalApiAuthHeaders.PLAYER_ID, "0");
            template.header(InternalApiAuthHeaders.TIMESTAMP, Long.toString(timestamp));
            template.header(InternalApiAuthHeaders.SIGNATURE, signature);
        };
    }
}
