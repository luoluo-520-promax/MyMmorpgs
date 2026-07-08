/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/.../config/ActivityPortConfiguration.java
 * 2) 所属模块：activity-service / config
 * 3) 主要职责：活动服独立部署时注册背包发奖等端口的兜底 Bean
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.port.NoOpActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 活动微服务端口 Bean 兜底配置（无 player-service 合部署时使用 NoOp 实现）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
public class ActivityPortConfiguration {

    @Bean
    ActivityItemGrantPort activityItemGrantPort() {
        return new NoOpActivityItemGrantPort();
    }

    @Bean
    PlayerNotificationPort playerNotificationPort() {
        return new NoOpPlayerNotificationPort();
    }

    @Bean
    PlayerDataLoadPort playerDataLoadPort() {
        return new NoOpPlayerDataLoadPort();
    }
}
