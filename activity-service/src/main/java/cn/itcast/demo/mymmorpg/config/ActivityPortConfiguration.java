/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/.../config/ActivityPortConfiguration.java
 * 2) 所属模块：activity-service / config
 * 3) 主要职责：活动服独立部署时注册背包发奖等端口；优先 Rest，缺 URL 时 NoOp
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.port.NoOpActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 活动微服务端口 Bean 兜底配置。
 * <p>
 * 发奖：当 {@code game.port.remote.enabled=true}（或 Rest Bean 已存在）时使用
 * {@link cn.itcast.demo.mymmorpg.port.remote.RestActivityItemGrantPort}；
 * 否则注册 NoOp（生产环境由 {@link ActivityGrantPortProductionValidator} fail-fast）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
public class ActivityPortConfiguration {

    @Bean
    @ConditionalOnMissingBean(ActivityItemGrantPort.class)
    ActivityItemGrantPort activityItemGrantPort() {
        return new NoOpActivityItemGrantPort();
    }

    @Bean
    @ConditionalOnMissingBean(PlayerNotificationPort.class)
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true)
    PlayerNotificationPort playerNotificationPort() {
        return new NoOpPlayerNotificationPort();
    }

    @Bean
    @ConditionalOnMissingBean(PlayerDataLoadPort.class)
    @ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "false", matchIfMissing = true)
    PlayerDataLoadPort playerDataLoadPort() {
        return new NoOpPlayerDataLoadPort();
    }
}
