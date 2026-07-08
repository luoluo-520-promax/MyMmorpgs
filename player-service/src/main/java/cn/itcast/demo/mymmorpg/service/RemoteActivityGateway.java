/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/RemoteActivityGateway.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：微服务模式下通过 Feign 将活动协议转发至 activity-service。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 微服务模式下通过 Feign 将活动协议转发至 activity-service

import cn.itcast.demo.mymmorpg.client.ActivityCommandClient; // OpenFeign 客户端，HTTP POST 至 activity-service
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 补齐 HTTP 响应体对应的 ScRsp msgId，供 Netty 写回客户端
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一封装 msgId + Protobuf payload 的二进制帧
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardCsReq; // 领取活动档位奖励 CsReq
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailCsReq; // 活动详情 CsReq（含 activityId）
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq; // 活动列表 CsReq
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // game.activity.remote.enabled=true 时才注册远程网关
import org.springframework.stereotype.Component; // 活动远程网关 Bean，替代本地 ActivityService 处理活动协议

/**
 * 活动远程网关：player-service 不本地执行业务，将 CsReq 原样转发 activity-service 并补齐 ScRsp msgId。
 * 仅在 {@code game.activity.remote.enabled=true} 时注册，替代本地 {@link ActivityService}。
 */
@Component // Feign 转发活动 CsReq 至 activity-service 并补齐 GET_ACTIVITY_LIST/DETAIL/CLAIM ScRsp msgId
@ConditionalOnProperty(name = "game.activity.remote.enabled", havingValue = "true") // 单体/本地模式不加载，走本地 ActivityService
public class RemoteActivityGateway implements RemoteActivityClient { // 活动远程网关：player-service 不本地执行业务，将 CsReq 原样转发 activity-service 并补齐 ScRsp msgId

    /** Feign 客户端，将 Protobuf 字节 POST 到 activity-service 对应 REST 端点 */
    private final ActivityCommandClient activityCommandClient; // Feign 客户端，将 Protobuf 字节 POST 到 activity-service 对应 REST 端点

    /**
     * 构造器：Feign ActivityCommandClient。
     */
    public RemoteActivityGateway(ActivityCommandClient activityCommandClient) { // 构造器：Feign ActivityCommandClient
        this.activityCommandClient = activityCommandClient; // HTTP POST activity-service /list/detail/claim
    }

    @Override // RemoteActivityClient.handleGetActivityList：Feign 查活动配置与玩家进度
    public ProtocolMessage handleGetActivityList(long playerId, GetActivityListCsReq req) { // Feign 转发 GET_ACTIVITY_LIST 至 activity-service
        byte[] payload = activityCommandClient.list(playerId, req.toByteArray()); // Feign 查 activity_config + 玩家进度
        return new ProtocolMessage(MessageId.GET_ACTIVITY_LIST_SC_RSP, payload); // 补齐 msgId=802 供客户端解析
    }

    @Override // RemoteActivityClient.handleGetActivityDetail：Feign 查单活动详情与档位状态
    public ProtocolMessage handleGetActivityDetail(long playerId, GetActivityDetailCsReq req) { // Feign 转发 GET_ACTIVITY_DETAIL 至 activity-service
        byte[] payload = activityCommandClient.detail(playerId, req.toByteArray()); // 远程查单活动详情与档位状态
        return new ProtocolMessage(MessageId.GET_ACTIVITY_DETAIL_SC_RSP, payload); // 补齐 msgId=804
    }

    @Override // RemoteActivityClient.handleClaimActivityReward：Feign 远程事务发奖写 player_activity_progress
    public ProtocolMessage handleClaimActivityReward(long playerId, ClaimActivityRewardCsReq req) { // Feign 转发 CLAIM_ACTIVITY_REWARD 远程发奖
        byte[] payload = activityCommandClient.claim(playerId, req.toByteArray()); // 远程事务发奖写 player_activity_progress
        return new ProtocolMessage(MessageId.CLAIM_ACTIVITY_REWARD_SC_RSP, payload); // 补齐 msgId=806，body 含 retcode 与道具
    }
}
