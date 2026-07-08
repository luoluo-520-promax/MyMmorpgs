/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/BattleFacade.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：战斗模块协议门面，解析 WebSocket 包并路由至 BattleCommandGateway。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // WebSocket 战斗模块 Modules.BATTLE 协议门面

import cn.itcast.demo.mymmorpg.handler.DispatchSession; // WebSocket 会话，提供当前选角 playerId
import cn.itcast.demo.mymmorpg.handler.MessageRoute; // 标注战斗模块号 Modules.BATTLE
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // 标注子命令 cmd 映射处理方法
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // 外层 Protobuf 包装，内层 payload 需 parseFrom
import cn.itcast.demo.mymmorpg.protocol.Modules; // 战斗模块协议号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一战斗响应 msgId + payload
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq;
import org.springframework.stereotype.Component; // 消息分发器扫描注册 BATTLE 模块 Handler

@Component // Spring 管理，供 GameServerHandler 反射分发
@MessageRoute(module = Modules.BATTLE) // WebSocket 路由 module=BATTLE，cmd=1/3/6 开战/行动/结算
public class BattleFacade { // 解析外层 GamePackets 并转发 BattleCommandGateway

    /** 战斗命令网关，本地/远程路由在此完成 */
    private final BattleCommandGateway battleCommandGateway; // cmd=1/3/6 开战/行动/结算

    public BattleFacade(BattleCommandGateway battleCommandGateway) { // 文件维护说明
        this.battleCommandGateway = battleCommandGateway; // 开战/行动/结算协议路由至 BattleService 或 battle-service
    }

    /** 发起战斗：module=BATTLE, cmd=1 */
    @RequestHandler(cmd = 1) // BattleStartCsReq
    public ProtocolMessage start(DispatchSession session, GamePackets.BattleStartCsReq pkt) throws Exception { // 文件维护说明
        long pid = session.playerId() != null ? session.playerId() : 0L; // 未选角时 playerId=0，BattleService 返回 PLAYER_NOT_SELECTED
        return battleCommandGateway.handleBattleStart(pid, // 文件维护说明
                BattleStartCsReq.parseFrom(pkt.payload())); // 解析内层请求并转发 BATTLE_START
    }

    /** 战斗行动：module=BATTLE, cmd=3 */
    @RequestHandler(cmd = 3) // BattleActionCsReq
    public ProtocolMessage action(DispatchSession session, GamePackets.BattleActionCsReq pkt) throws Exception { // 文件维护说明
        long pid = session.playerId() != null ? session.playerId() : 0L; // 当前战斗中的角色 ID
        return battleCommandGateway.handleBattleAction(pid, // 文件维护说明
                BattleActionCsReq.parseFrom(pkt.payload())); // 提交 BATTLE_ACTION 回合操作
    }

    /** 结束战斗：module=BATTLE, cmd=6 */
    @RequestHandler(cmd = 6) // BattleEndCsReq
    public ProtocolMessage end(DispatchSession session, GamePackets.BattleEndCsReq pkt) throws Exception { // 文件维护说明
        long pid = session.playerId() != null ? session.playerId() : 0L; // 结算关联当前角色
        return battleCommandGateway.handleBattleEnd(pid, // 文件维护说明
                BattleEndCsReq.parseFrom(pkt.payload())); // 请求 BATTLE_END 结算
    }
}
