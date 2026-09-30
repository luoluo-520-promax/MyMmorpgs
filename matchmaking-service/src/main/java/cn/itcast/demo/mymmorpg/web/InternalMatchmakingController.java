/**
 * 文件维护说明：`matchmaking-service` 对外提供内部匹配接口的控制器。
 * <p>
 * 路径：`matchmaking-service/src/main/java/cn/itcast/demo/mymmorpg/web/InternalMatchmakingController.java`
 * <br>模块：匹配服内部 HTTP 接口
 * <br>职责：接收玩家发起的入队与取消匹配请求，并将二进制协议体交由 `MatchmakingService` 处理后返回协议响应。
 * <br>变更建议：仅在调整内部匹配协议、鉴权头或接口路径时修改；新增接口时需保持与网关和调用方协议一致。
 * <br>风险提示：该控制器直接承载匹配状态变更，请勿在这里加入额外业务分支，避免破坏队列一致性或响应码语义。
 */
package cn.itcast.demo.mymmorpg.web; // 匹配服内部 Web 接口包，承载玩家入队与取消匹配请求

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 协议消息封装，用于返回消息号与二进制响应体
import cn.itcast.demo.mymmorpg.protocol.protobuf.CancelMatchCsReq; // 取消匹配客户端请求体，用于解析 queueId
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnqueueMatchCsReq; // 入队匹配客户端请求体，用于解析匹配类型和模式
import cn.itcast.demo.mymmorpg.service.MatchmakingService; // 匹配服务，封装入队、匹配和取消逻辑
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅在匹配服进程内启用该控制器
import org.springframework.http.MediaType; // 指定二进制协议的请求与响应媒体类型
import org.springframework.http.ResponseEntity; // 封装 HTTP 响应体，返回 protobuf 二进制内容
import org.springframework.web.bind.annotation.PostMapping; // 声明 POST 接口，符合内部命令式调用习惯
import org.springframework.web.bind.annotation.RequestBody; // 读取请求体中的 protobuf 二进制
import org.springframework.web.bind.annotation.RequestHeader; // 读取玩家身份头，确保请求归属明确
import org.springframework.web.bind.annotation.RequestMapping; // 统一内部匹配接口前缀
import org.springframework.web.bind.annotation.RestController; // 声明 REST 控制器

/**
 * 处理玩家入队与取消匹配的内部控制器
 */
@RestController // 将该类作为内部 REST 控制器对外暴露匹配接口
@ConditionalOnProperty(name = "spring.application.name", havingValue = "matchmaking-service") // 仅在匹配服务实例中启用，避免其他模块误注册
@RequestMapping("/internal/match") // 内部匹配接口统一前缀，供调用方按模块路由
public class InternalMatchmakingController {

    private final MatchmakingService matchmakingService; // 匹配业务服务，负责所有队列状态与匹配结果计算

    public InternalMatchmakingController(MatchmakingService matchmakingService) { // 构造注入匹配服务，保证控制器无状态
        this.matchmakingService = matchmakingService; // 保存服务引用，供入队和取消接口复用
    }

    /**
     * 玩家发起入队时解析头部玩家 ID 与请求体
     * @param playerId
     * @param body
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/enqueue", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // 以二进制协议接收入队请求并返回二进制响应
    public ResponseEntity<byte[]> enqueue(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = matchmakingService.handleEnqueueMatch(playerId, EnqueueMatchCsReq.parseFrom(body)); // 反序列化入队请求并交给业务层完成入队与尝试匹配
        return ResponseEntity.ok(msg.payload()); // 直接返回 protobuf 响应体，供上层客户端继续解析
    }

    /**
     * 玩家发起取消匹配时解析头部玩家 ID 与请求体
     * @param playerId
     * @param body
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/cancel", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // 以二进制协议接收取消匹配请求并返回二进制响应
    public ResponseEntity<byte[]> cancel(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = matchmakingService.handleCancelMatch(playerId, CancelMatchCsReq.parseFrom(body)); // 反序列化取消请求并交给业务层校验队列后移除
        return ResponseEntity.ok(msg.payload()); // 返回取消结果的 protobuf 响应体，供客户端更新界面状态
    }

    /**
     * 排队中心跳：刷新 match:heartbeat TTL，避免断线幽灵占位。
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<java.util.Map<String, Object>> heartbeat(@RequestHeader("X-Player-Id") long playerId) {
        boolean ok = matchmakingService.touchHeartbeat(playerId);
        return ResponseEntity.ok(java.util.Map.of("ok", ok, "playerId", playerId));
    }
}
