/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/RemoteBattleGateway.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：微服务模式下通过 Feign 将战斗协议转发至 battle-service。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 微服务模式下通过 Feign 将战斗协议转发至 battle-service

import cn.itcast.demo.mymmorpg.client.BattleCommandClient; // OpenFeign 客户端，HTTP POST 至 battle-service
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 补齐 HTTP 响应体对应的 ScRsp msgId
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一封装 msgId + Protobuf payload
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq; // 战斗回合行动 CsReq（技能/普攻）
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq; // 结束战斗 CsReq（结算请求）
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq; // 开始战斗 CsReq（含 enemyEntityId）
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // game.battle.remote.enabled=true 时才注册远程网关
import org.springframework.stereotype.Component; // 战斗远程网关 Bean，替代本地 BattleService 处理战斗协议

/**
 * 战斗远程网关：player-service 校验场景怪物后，将战斗回合逻辑委托 battle-service 执行。
 * 仅在 {@code game.battle.remote.enabled=true} 时注册，替代本地 BattleService。
 */
@Component // Feign 转发战斗 CsReq 至 battle-service 并补齐 BATTLE_START/ACTION/END ScRsp msgId
@ConditionalOnProperty(name = "game.battle.remote.enabled", havingValue = "true") // 单体模式不加载，走本地 BattleService
public class RemoteBattleGateway implements RemoteBattleClient { // 战斗远程网关：player-service 校验场景怪物后，将战斗回合逻辑委托 battle-service 执行

    /** Feign 客户端：将 Protobuf 字节 POST 到 battle-service /start、/action、/end */
    private final BattleCommandClient battleCommandClient; // Feign 客户端：将 Protobuf 字节 POST 到 battle-service /start、/action、/end

    /**
     * 构造器：Feign BattleCommandClient。
     */
    public RemoteBattleGateway(BattleCommandClient battleCommandClient) { // 构造器：Feign BattleCommandClient
        this.battleCommandClient = battleCommandClient; // HTTP POST battle-service 开战/行动/结算
    }

    @Override // RemoteBattleClient.handleBattleStart：Feign 远程创建战斗实例与首回合信息
    public ProtocolMessage handleBattleStart(long playerId, BattleStartCsReq req) { // Feign 转发 BATTLE_START 至 battle-service 创建战斗实例
        byte[] payload = battleCommandClient.start(playerId, req.toByteArray()); // 远程创建战斗实例与首回合信息
        return new ProtocolMessage(MessageId.BATTLE_START_SC_RSP, payload); // 补齐 BATTLE_START_SC_RSP 进战斗 UI
    }

    @Override // RemoteBattleClient.handleBattleAction：Feign 远程单回合伤害/Buff/胜负
    public ProtocolMessage handleBattleAction(long playerId, BattleActionCsReq req) { // Feign 转发 BATTLE_ACTION 执行单回合战斗逻辑
        byte[] payload = battleCommandClient.action(playerId, req.toByteArray()); // 远程单回合伤害/Buff/胜负
        return new ProtocolMessage(MessageId.BATTLE_ACTION_SC_RSP, payload); // 补齐 BATTLE_ACTION_SC_RSP HP 快照
    }

    @Override // RemoteBattleClient.handleBattleEnd：Feign 远程结算经验/掉落写库
    public ProtocolMessage handleBattleEnd(long playerId, BattleEndCsReq req) { // Feign 转发 BATTLE_END 远程结算经验与掉落
        byte[] payload = battleCommandClient.end(playerId, req.toByteArray()); // 远程结算经验/掉落写库
        return new ProtocolMessage(MessageId.BATTLE_END_SC_RSP, payload); // 补齐 BATTLE_END_SC_RSP 奖励列表
    }
}
