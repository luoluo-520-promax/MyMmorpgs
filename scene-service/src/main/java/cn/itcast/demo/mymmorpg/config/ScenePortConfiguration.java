package cn.itcast.demo.mymmorpg.config; // scene-service 端口适配层配置，为独立部署提供 NoOp 实现

import cn.itcast.demo.mymmorpg.port.NoOpPlayerCachePort; // 空实现 PlayerCachePort：findById 恒返回 null，scene-service 独立跑时不查 MySQL
import cn.itcast.demo.mymmorpg.port.NoOpPlayerNotificationPort; // 空实现 PlayerNotificationPort：send/unbind 均为空操作，无 WebSocket 推送能力
import cn.itcast.demo.mymmorpg.port.PlayerCachePort; // 玩家数据读端口，SceneActorService 进场景时 findById 取 name/level
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort; // 玩家下行推送端口，SceneActorService 广播 SYNC_ENTITY_SC_NOTIFY 时使用
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 限定本配置仅在 scene-service 进程生效
import org.springframework.context.annotation.Bean; // 将 NoOp 实现注册为 Spring Bean
import org.springframework.context.annotation.Configuration; // 声明配置类

/**
 * 为 scene-service 独立部署注入 PlayerCachePort / PlayerNotificationPort 的占位实现
 */
@Configuration(proxyBeanMethods = false) // 无 Bean 间方法调用，关闭代理以减轻启动开销
@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service") // 与 SceneServiceApplication 相同条件，防止 player-service 重复注册 NoOp Bean
public class ScenePortConfiguration {
    /** 注册 PlayerCachePort Bean：scene-service 独立部署时 findById 恒返回 null */
    @Bean // 注册 PlayerCachePort Bean；player-service 聚合部署时会用自己的真实实现覆盖
    PlayerCachePort playerCachePort() {
        return new NoOpPlayerCachePort(); // 独立 scene-service 无 player 表连接，进场景需由上游网关注入真实 Port 或走 RPC
    }
    /** 注册 PlayerNotificationPort Bean：scene-service 独立部署时 send/unbind 为空操作 */
    @Bean // 注册 PlayerNotificationPort Bean
    PlayerNotificationPort playerNotificationPort() {
        return new NoOpPlayerNotificationPort(); // 独立部署时 SYNC notify 不会下发，需 player-service 提供真实 WebSocket 绑定
    }
}
