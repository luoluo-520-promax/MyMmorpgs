/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/BattleCommandGateway.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：战斗命令路由网关，按配置在本地 BattleService 与远程 battle-service 间切换。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 战斗协议 cmd=1/3/6 本地/远程路由网关

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 战斗响应统一封装 msgId + payload
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq; // cmd=3 战斗回合行动请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq; // cmd=6 战斗结束/结算请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq; // cmd=1 发起战斗请求
import org.springframework.beans.factory.ObjectProvider; // RemoteBattleClient 仅在 remote.enabled 时有 Bean
import org.springframework.beans.factory.annotation.Value; // 读取 game.battle.remote.enabled 开关
import org.springframework.stereotype.Component; // BattleFacade 经本网关调用战斗业务

/**
 * 战斗命令网关：默认本地 {@link BattleService}，player-service 可切换为远程 battle-service。
 * GameServerHandler / BattleFacade 统一经本类调用，屏蔽单体与微服务差异。
 */
@Component // 全局单例，BattleFacade 统一经此路由战斗协议
public class BattleCommandGateway { // game.battle.remote.enabled 控制 Feign 与本地 BattleService 切换

    /** 本地战斗业务（mmorpg-common 或 battle-service 内 BattleService 实现） */
    private final BattleService localService; // 本地内存/Redis 战斗回合计算

    /** 远程 Feign 客户端，player-service + remote.enabled=true 时有 Bean */
    private final ObjectProvider<RemoteBattleClient> remoteClient; // RemoteBattleGateway Feign 实现

    /** game.battle.remote.enabled：true 且 Feign 可用时路由至 battle-service */
    private final boolean remoteEnabled; // false 时始终走本地 BattleService

    public BattleCommandGateway( // 战斗命令网关：默认本地 {@link BattleService}，player-service 可切换为远程 battle-service
            BattleService localService, // 本地内存/Redis 战斗回合计算
            ObjectProvider<RemoteBattleClient> remoteClient, // RemoteBattleGateway Feign 实现
            @Value("${game.battle.remote.enabled:false}") boolean remoteEnabled) { // 微服务模式开关，默认单体本地处理
        this.localService = localService; // 单体部署走本地 BattleService
        this.remoteClient = remoteClient; // 远程模式走 Feign battle-service
        this.remoteEnabled = remoteEnabled; // false 时始终本地处理
    }

    /** 开始战斗 cmd=1：创建战斗实例、初始化双方属性 */
    public ProtocolMessage handleBattleStart(long playerId, BattleStartCsReq req) { // 开始战斗 cmd=1：创建战斗实例、初始化双方属性
        if (useRemote()) { // game.battle.remote.enabled=true 且 Feign 已注册
            return remoteClient.getObject().handleBattleStart(playerId, req); // Feign → battle-service 创建战斗实例
        }
        return localService.handleBattleStart(playerId, req); // 本地 Redis/内存创建 battle:{battleId} 实例
    }

    /** 战斗行动 cmd=3：提交技能/普攻等回合操作 */
    public ProtocolMessage handleBattleAction(long playerId, BattleActionCsReq req) { // 战斗行动 cmd=3：提交技能/普攻等回合操作
        if (useRemote()) { // 微服务模式且 Feign 已注册
            return remoteClient.getObject().handleBattleAction(playerId, req); // 远程推进战斗回合并计算伤害
        }
        return localService.handleBattleAction(playerId, req); // 本地计算伤害、Buff 与回合状态
    }

    /** 结束战斗 cmd=6：结算经验、掉落并销毁战斗实例 */
    public ProtocolMessage handleBattleEnd(long playerId, BattleEndCsReq req) { // 结束战斗 cmd=6：结算经验、掉落并销毁战斗实例
        if (useRemote()) { // 微服务模式且 Feign 已注册
            return remoteClient.getObject().handleBattleEnd(playerId, req); // 远程结算经验/掉落并销毁实例
        }
        return localService.handleBattleEnd(playerId, req); // 本地结算并发奖，DEL battle:{battleId}
    }

    /** 判断是否走远程：开关打开且 Feign 实现已注册 */
    private boolean useRemote() { // 判断是否走远程：开关打开且 Feign 实现已注册
        return remoteEnabled && remoteClient.getIfAvailable() != null; // 双条件满足才路由至 battle-service
    }
}
