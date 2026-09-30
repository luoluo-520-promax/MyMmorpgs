package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.service.SceneReconnectStore;
import cn.itcast.demo.mymmorpg.service.SessionKickService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将场景重连宽限期接到踢线策略：保护期内同设备不踢。
 */
@Configuration
public class ReconnectGraceConfiguration {

    @Bean
    @ConditionalOnBean(SceneReconnectStore.class)
    public SessionKickService.ReconnectGraceQuery reconnectGraceQuery(SceneReconnectStore store) {
        return playerId -> store.peek(playerId) != null;
    }
}
