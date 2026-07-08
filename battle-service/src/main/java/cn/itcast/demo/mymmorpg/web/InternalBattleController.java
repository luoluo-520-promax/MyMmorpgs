/**
 * 文件说明：内部战斗 HTTP 控制器。
 * 职责：供 player-service 通过 Feign 调用的内部战斗 HTTP 接口，入参/出参均为 Protobuf 字节流。
 * 约定：请求体为 CsReq 二进制，响应体仅含业务字段（ScRsp body），msgId 由调用方补齐。
 * 风险提示：/internal 路径应限制外网访问。
 */
package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.BattleService; // 战斗核心业务
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一协议返回
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq; // 行动请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq; // 结束请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq; // 开始请求
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按应用名条件注册
import org.springframework.http.MediaType; // APPLICATION_OCTET_STREAM
import org.springframework.http.ResponseEntity; // HTTP 200 + body
import org.springframework.web.bind.annotation.PostMapping; // POST 映射
import org.springframework.web.bind.annotation.RequestBody; // 请求体绑定
import org.springframework.web.bind.annotation.RequestHeader; // 请求头绑定
import org.springframework.web.bind.annotation.RequestMapping; // 路径前缀
import org.springframework.web.bind.annotation.RestController; // REST 控制器

/**
 * 内部命令型接口：供 player-service 通过 Feign 调用。
 * 约定：
 * - 请求体为 Protobuf 二进制（CsReq）
 * - 响应体仅含业务字段（ScRsp body），不含 msgId；由 RemoteBattleGateway 补齐后写回客户端
 */
@RestController // REST 控制器
@ConditionalOnProperty(name = "spring.application.name", havingValue = "battle-service") // 仅 battle-service 进程注册
@RequestMapping("/internal/battle") // 内部战斗路径前缀
public class InternalBattleController { // Feign 服务端点

    /** 战斗核心业务服务 */
    private final BattleService battleService; // 委托战斗业务

    /**
     * 构造器注入战斗服务。
     *
     * @param battleService 战斗业务服务
     */
    public InternalBattleController(BattleService battleService) { // 构造器注入
        this.battleService = battleService; // 保存服务引用
    }

    /**
     * 开始战斗内部接口。
     *
     * @param playerId 玩家 ID（来自 X-Player-Id 请求头）
     * @param body     BattleStartCsReq Protobuf 字节
     * @return ScRsp 二进制载荷
     */
    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // 开始战斗
    public ResponseEntity<byte[]> start(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception { // playerId + CsReq 字节
        ProtocolMessage msg = battleService.handleBattleStart(playerId, BattleStartCsReq.parseFrom(body)); // 校验阵容与敌人，创建战斗实例并快照双方属性
        return ResponseEntity.ok(msg.payload()); // Feign 只传 body：battleId、敌我属性、sceneId（msgId 由调用方补齐）
    }

    /**
     * 战斗行动内部接口。
     *
     * @param playerId 玩家 ID
     * @param body     BattleActionCsReq Protobuf 字节
     * @return ScRsp 二进制载荷
     */
    @PostMapping(value = "/action", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // 战斗行动
    public ResponseEntity<byte[]> action(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception { // 行动请求
        ProtocolMessage msg = battleService.handleBattleAction(playerId, BattleActionCsReq.parseFrom(body)); // 执行回合行动并更新战斗状态
        return ResponseEntity.ok(msg.payload()); // body 含 actionId、伤害/治疗数值，供客户端播放回合表现
    }

    /**
     * 结束战斗内部接口。
     *
     * @param playerId 玩家 ID
     * @param body     BattleEndCsReq Protobuf 字节
     * @return ScRsp 二进制载荷
     */
    @PostMapping(value = "/end", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // 结束战斗
    public ResponseEntity<byte[]> end(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception { // 结束请求
        ProtocolMessage msg = battleService.handleBattleEnd(playerId, BattleEndCsReq.parseFrom(body)); // 结算战斗、清理内存状态并计算奖励
        return ResponseEntity.ok(msg.payload()); // body 含经验、货币与掉落道具，供背包与等级系统入账
    }
}
