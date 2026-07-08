package cn.itcast.demo.mymmorpg.config; // chat-service 端口适配配置，为独立部署提供 NoOp 玩家缓存与推送实现

import cn.itcast.demo.mymmorpg.port.NoOpPlayerCachePort; // 空实现：findById 返回 null，existsById 返回 false
import cn.itcast.demo.mymmorpg.port.NoOpPlayerNotificationPort; // 空实现：isOnline 恒 false，send/broadcast 不实际推送
import cn.itcast.demo.mymmorpg.port.PlayerCachePort; // 查玩家 Player 实体与存在性，ChatService 私聊校验用
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort; // WebSocket 下行推送 603、判断在线、批量 sendToPlayers
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅 chat-service 进程注册本配置，player-service 用真实 Feign 实现
import org.springframework.context.annotation.Bean; // 注册 PlayerCachePort、PlayerNotificationPort Bean
import org.springframework.context.annotation.Configuration; // 声明配置类

/**
 * chat-service 独立部署时的端口 Bean：使用 NoOp 实现占位。
 * 集成到 player-service 时由 player 模块提供真实 Feign/Redis 实现覆盖。
 */
@Configuration(proxyBeanMethods = false) // 关闭配置类 CGLIB 代理
@ConditionalOnProperty(name = "spring.application.name", havingValue = "chat-service") // spring.application.name 非 chat-service 时不加载
public class ChatPortConfiguration { // 为聊天服务注入跨模块端口适配 Bean

    /**
     * 注册玩家缓存端口 NoOp 实现：独立 chat-service 无 player DB 时占位。
     * 生产环境应替换为 Feign 调用 player-service 或 Redis 缓存实现。
     */
    @Bean // 注册 PlayerCachePort 单例，ChatService 构造器注入
    PlayerCachePort playerCachePort() { // 容器启动时创建，供 ChatService 查发送者 Player 与私聊目标存在性
        return new NoOpPlayerCachePort(); // 所有 findById 返回 null，私聊/世界频道在独立部署下会 PLAYER_NOT_FOUND
    }

    /**
     * 注册玩家通知端口 NoOp 实现：独立 chat-service 无 WebSocket 网关时占位。
     * 生产环境应替换为 Redis Pub/Sub 或 Feign 推送到 gateway/player-service。
     */
    @Bean // 注册 PlayerNotificationPort 单例，ChatService 构造器注入
    PlayerNotificationPort playerNotificationPort() { // 容器启动时创建，供 ChatService 推送 603 与判断在线
        return new NoOpPlayerNotificationPort(); // send/broadcast 空操作，isOnline 恒 false，私聊会 TARGET_OFFLINE
    }
}
