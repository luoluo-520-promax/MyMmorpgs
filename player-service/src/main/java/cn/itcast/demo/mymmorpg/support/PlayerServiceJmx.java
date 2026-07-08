/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/support/PlayerServiceJmx.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/support
 * 3) 主要职责：JMX 暴露在线玩家数与单玩家在线状态，基于 PlayerSessionService Redis 会话。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.support; // player-service 业务策略接口与 JMX/ConfigManager

import cn.itcast.demo.mymmorpg.service.PlayerSessionService; // 领域 Service 委托（本文件为 Facade/Handler）
import org.springframework.jmx.export.annotation.ManagedAttribute; // ManagedAttribute，PlayerServiceJmx.java 编译依赖
import org.springframework.jmx.export.annotation.ManagedOperation; // ManagedOperation，PlayerServiceJmx.java 编译依赖
import org.springframework.jmx.export.annotation.ManagedResource; // ManagedResource，PlayerServiceJmx.java 编译依赖
import org.springframework.stereotype.Component; // Spring 组件 stereotype 注解
/**
 * JMX 玩家会话运维：压测或运维排查某 playerId 是否仍在线。
 */

@Component // Spring 单例组件
@ManagedResource( // @ManagedResource 注解
        objectName = "cn.itcast.demo.mymmorpg:type=PlayerService,name=Player", // PlayerServiceJmx 逻辑
        description = "玩家服务（会话与在线）" // PlayerServiceJmx 逻辑
) // PlayerServiceJmx 逻辑

public class PlayerServiceJmx { // PlayerServiceJmx 类型定义
    private final PlayerSessionService sessionService; // Redis player:online:{playerId} 会话管理
    public PlayerServiceJmx(PlayerSessionService sessionService) { // 构造 PlayerServiceJmx，注入 PlayerSessionService sessionService
        this.sessionService = sessionService; // 注入 Redis 在线会话服务
    } // PlayerServiceJmx 方法体结束
    /** Redis 中当前在线玩家计数，与网关/WebSocket 连接数交叉验证 */

    @ManagedAttribute(description = "当前 Redis 中记录的在线玩家数") // @ManagedAttribute 注解
    public long getOnlinePlayerCount() { // 读取 OnlinePlayerCount（OnlinePlayerCount）
        return sessionService.countOnline(); // JConsole 查看全服在线人数
    } // getOnlinePlayerCount 方法体结束
    /** 查询单个玩家是否仍在在线集合中 */

    @ManagedOperation(description = "某玩家是否在线") // @ManagedOperation 注解
    public boolean isPlayerOnline(long playerId) { // 判断 PlayerOnline 是否为真
        return sessionService.isOnline(playerId); // GM 排查指定 playerId 在线态
    } // isPlayerOnline 方法体结束
} // PlayerServiceJmx 类体结束
