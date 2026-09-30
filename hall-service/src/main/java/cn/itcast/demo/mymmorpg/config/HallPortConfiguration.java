package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.MailItemGrantPort;
import cn.itcast.demo.mymmorpg.port.NoOpMailItemGrantPort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerCachePort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerProgressPort;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 大厅服本地端口占位：远程关闭时提供 NoOp；远程开启时由 Rest*Port 提供。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.application.name", havingValue = "hall-service")
public class HallPortConfiguration {

    @Bean
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(PlayerCachePort.class)
    PlayerCachePort playerCachePort() {
        return new NoOpPlayerCachePort();
    }

    @Bean
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(PlayerProgressPort.class)
    PlayerProgressPort playerProgressPort() {
        return new NoOpPlayerProgressPort();
    }

    @Bean
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(MailItemGrantPort.class)
    MailItemGrantPort mailItemGrantPort() {
        return new NoOpMailItemGrantPort();
    }
}
