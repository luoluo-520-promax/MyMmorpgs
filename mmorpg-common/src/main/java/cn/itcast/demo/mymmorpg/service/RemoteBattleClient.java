/**
 * 战斗服务远程 HTTP 调用端口（Feign 适配层）。
 * player-service 在微服务模式下，将战斗相关协议转发至 battle-service 处理。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一协议响应封装，含 retcode 与 protobuf 载荷
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq; // 战斗回合内行动（普攻/技能/道具）请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq; // 战斗结束（胜利/逃跑/超时）请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq; // 发起 PVE/PVP 战斗的客户端请求

/**
 * 战斗远程调用端口：player-service 在微服务模式下通过 Feign 实现；
 * 单体模式下 BattleCommandGateway 直接使用本地 BattleService，不注入本接口实现。
 */
public interface RemoteBattleClient {

    /** 远程处理开始战斗，返回 BATTLE_START_SC_RSP（含战场快照、先手顺序等） */
    ProtocolMessage handleBattleStart(long playerId, BattleStartCsReq req); // 创建战斗实例并返回初始战场数据

    /** 远程处理战斗行动，返回 BATTLE_ACTION_SC_RSP（伤害、Buff 变化、回合推进） */
    ProtocolMessage handleBattleAction(long playerId, BattleActionCsReq req); // 执行回合内操作并推进战斗状态

    /** 远程处理结束战斗，返回 BATTLE_END_SC_RSP（结算奖励、经验、掉落） */
    ProtocolMessage handleBattleEnd(long playerId, BattleEndCsReq req); // 触发战斗结算与奖励发放
}
