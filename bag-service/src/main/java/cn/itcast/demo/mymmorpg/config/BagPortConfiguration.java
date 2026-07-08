package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.NoOpPlayerCachePort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerProgressPort;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.application.name", havingValue = "bag-service")
public class BagPortConfiguration {

    @Bean
    PlayerCachePort playerCachePort() {
        return new NoOpPlayerCachePort();
    }

    @Bean
    PlayerProgressPort playerProgressPort() {
        return new NoOpPlayerProgressPort();
    }

    @Bean
    PlayerDataLoadPort playerDataLoadPort() {
        return new NoOpPlayerDataLoadPort();
    }
}
