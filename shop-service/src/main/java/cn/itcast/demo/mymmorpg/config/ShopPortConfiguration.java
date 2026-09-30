package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.NoOpPlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.application.name", havingValue = "shop-service")
public class ShopPortConfiguration {

    @Bean
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true)
    PlayerNotificationPort playerNotificationPort() {
        return new NoOpPlayerNotificationPort();
    }
}
