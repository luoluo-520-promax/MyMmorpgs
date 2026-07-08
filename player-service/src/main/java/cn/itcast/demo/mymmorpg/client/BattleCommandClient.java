/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/client/BattleCommandClient.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/client
 * 3) 主要职责：Feign 声明式客户端，player-service 微服务模式下 HTTP 调用 battle-service 内部战斗 API。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.client; // player-service Feign 客户端，远程调用 battle/activity

import org.springframework.cloud.openfeign.FeignClient; // Feign 远程调用 FeignClient
import org.springframework.http.MediaType; // HTTP Content-Type 与响应体
import org.springframework.web.bind.annotation.PostMapping; // Spring MVC REST 映射注解
import org.springframework.web.bind.annotation.RequestBody; // Spring MVC REST 映射注解
import org.springframework.web.bind.annotation.RequestHeader; // Spring MVC REST 映射注解
/**
 * player-service -> battle-service 内部战斗命令 Feign 客户端。
 * 微服务模式下由 RemoteBattleGateway 注入并封装 Protobuf 编解码。
 */

@FeignClient(name = "battle-service", contextId = "battleCommandClient") // Nacos 服务名 battle-service

public interface BattleCommandClient { // BattleCommandClient 接口定义
    /**
     * 发起战斗：body 为 BattleStartCsReq 字节，响应含 battleId 与敌我属性快照。
     */

    @PostMapping(value = "/internal/battle/start", // HTTP POST 端点
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, // BattleCommandClient 逻辑
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // BattleCommandClient 逻辑
    byte[] start(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body); // 远程创建战斗实例
    /**
     * 提交战斗行动（攻击/技能/道具等）：body 为 BattleActionCsReq。
     */

    @PostMapping(value = "/internal/battle/action", // HTTP POST 端点
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, // BattleCommandClient 逻辑
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // BattleCommandClient 逻辑
    byte[] action(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body); // 远程提交回合行动
    /**
     * 结束战斗并结算：body 为 BattleEndCsReq，释放 battle-service 内存战斗实例。
     */

    @PostMapping(value = "/internal/battle/end", // HTTP POST 端点
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, // BattleCommandClient 逻辑
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // BattleCommandClient 逻辑
    byte[] end(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body); // 远程结束战斗并结算
} // BattleCommandClient 类体结束
