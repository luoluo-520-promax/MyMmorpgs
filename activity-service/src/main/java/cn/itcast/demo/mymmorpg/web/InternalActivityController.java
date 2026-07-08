/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/web/InternalActivityController.java
 * 2) 所属模块：activity-service / web
 * 3) 主要职责：供 player-service 通过 Feign 调用的内部活动 HTTP 端点，承载 Protobuf 二进制请求/响应
 * 4) 系统位置：活动微服务的对内 REST 层，不直接面向客户端
 * 5) 变更建议：新增内部接口时保持 octet-stream 与 X-Player-Id 约定，msgId 由调用方补齐
 */
package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 业务统一返回封装
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardCsReq; // 领取奖励请求 Protobuf
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailCsReq; // 活动详情请求 Protobuf
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq; // 活动列表请求 Protobuf
import cn.itcast.demo.mymmorpg.service.ActivityService; // 活动核心业务逻辑
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按应用名条件注册 Bean
import org.springframework.http.MediaType; // HTTP 媒体类型（octet-stream）
import org.springframework.http.ResponseEntity; // HTTP 响应包装
import org.springframework.web.bind.annotation.PostMapping; // POST 映射注解
import org.springframework.web.bind.annotation.RequestBody; // 请求体参数
import org.springframework.web.bind.annotation.RequestHeader; // 请求头参数
import org.springframework.web.bind.annotation.RequestMapping; // 类级路径前缀
import org.springframework.web.bind.annotation.RestController; // REST 控制器标记

/**
 * 内部命令型接口：供 player-service 通过 Feign 调用。
 * HTTP body 仅承载 Protobuf 业务字段；msgId 由 RemoteActivityGateway 按接口约定补齐。
 */
@RestController // 标记为 REST 控制器，返回值直接写入响应体
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service") // 仅在 activity-service 应用内生效
@RequestMapping("/internal/activity") // 内部活动 API 根路径
public class InternalActivityController { // Feign 服务端点实现

    private final ActivityService activityService; // 委托活动业务层处理

    /**
     * 构造器注入活动服务。
     *
     * @param activityService 活动核心业务 Bean
     */
    public InternalActivityController(ActivityService activityService) { // 构造器注入
        this.activityService = activityService; // 保存活动服务引用
    }

    /**
     * 获取玩家可见的活动列表。
     *
     * @param playerId 玩家 ID（来自请求头 X-Player-Id）
     * @param body     Protobuf 序列化的 GetActivityListCsReq 字节
     * @return Protobuf 序列化的 GetActivityListScRsp 字节
     */
    @PostMapping(value = "/list", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // 活动列表接口
    public ResponseEntity<byte[]> list(@RequestHeader("X-Player-Id") long playerId, @RequestBody(required = false) byte[] body) throws Exception {
        ProtocolMessage msg = activityService.handleGetActivityList(playerId, GetActivityListCsReq.parseFrom(body == null ? new byte[0] : body)); // 校验玩家并组装可见活动简要列表
        return ResponseEntity.ok(msg.payload()); // Feign 只传 body：retcode、loading、ActivityBriefInfo 列表（msgId 802 由调用方补齐）
    }

    /**
     * 获取单个活动的详情与奖励档位状态。
     *
     * @param playerId 玩家 ID
     * @param body     Protobuf 序列化的 GetActivityDetailCsReq 字节
     * @return Protobuf 序列化的 GetActivityDetailScRsp 字节
     */
    @PostMapping(value = "/detail", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // 活动详情接口
    public ResponseEntity<byte[]> detail(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception { // playerId + Protobuf 字节
        ProtocolMessage msg = activityService.handleGetActivityDetail(playerId, GetActivityDetailCsReq.parseFrom(body)); // 校验活动开启状态并填充 detail JSON 与档位状态
        return ResponseEntity.ok(msg.payload()); // body 含活动三态、detailData、各档 RewardStatus（可领/已领）
    }

    /**
     * 领取活动奖励（单档或一键领取）。
     *
     * @param playerId 玩家 ID
     * @param body     Protobuf 序列化的 ClaimActivityRewardCsReq 字节
     * @return Protobuf 序列化的 ClaimActivityRewardScRsp 字节
     */
    @PostMapping(value = "/claim", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE) // 领取奖励接口
    public ResponseEntity<byte[]> claim(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception { // 领取接口
        ProtocolMessage msg = activityService.handleClaimActivityReward(playerId, ClaimActivityRewardCsReq.parseFrom(body)); // 事务内校验条件、发背包并写入 Redis 进度
        return ResponseEntity.ok(msg.payload()); // body 含 retcode、本次发放道具、claimedCount（单档或一键领取）
    }
}
