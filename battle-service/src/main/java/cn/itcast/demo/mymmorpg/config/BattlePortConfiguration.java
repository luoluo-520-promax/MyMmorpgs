/**
 * 文件维护说明
 * 1) 文件路径：battle-service/src/main/java/.../config/BattlePortConfiguration.java
 * 2) 所属模块：battle-service / config
 * 3) 主要职责：战斗服独立部署时注册推送、场景、进度等端口的兜底 Bean
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.port.NoOpBattleScenePort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.NoOpPlayerProgressPort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 战斗微服务端口 Bean 兜底配置（无 player-service 合部署时使用 NoOp 实现）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.application.name", havingValue = "battle-service")
public class BattlePortConfiguration {

    @Bean
    PlayerNotificationPort playerNotificationPort() {
        return new NoOpPlayerNotificationPort();
    }

    @Bean
    BattleScenePort battleScenePort() {
        return new NoOpBattleScenePort();
    }

    @Bean
    PlayerProgressPort playerProgressPort() {
        return new NoOpPlayerProgressPort();
    }
}
