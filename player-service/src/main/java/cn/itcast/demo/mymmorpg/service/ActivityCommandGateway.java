/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/ActivityCommandGateway.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：活动命令路由网关，按配置在本地 ActivityService 与远程 activity-service 间切换。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 活动协议 cmd=1/3/5 本地/远程路由网关

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一 msgId + payload 响应，供 ActivityFacade 原样返回
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardCsReq; // cmd=5 领取活动奖励请求体
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailCsReq; // cmd=3 活动详情请求体
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq; // cmd=1 活动列表请求体
import org.springframework.beans.factory.ObjectProvider; // RemoteActivityClient 仅在 remote.enabled 时有 Bean
import org.springframework.beans.factory.annotation.Value; // 读取 game.activity.remote.enabled 开关
import org.springframework.stereotype.Component; // ActivityFacade 经本网关屏蔽本地/远程差异

/**
 * 活动命令网关：默认本地 {@link ActivityService}，player-service 可切换为远程 activity-service。
 */
@Component // 全局单例，ActivityFacade 统一经此路由活动协议
public class ActivityCommandGateway { // game.activity.remote.enabled 控制 Feign 与本地 ActivityService 切换

    /** 本地活动业务实现，单体模式或 remote.enabled=false 时使用 */
    private final ActivityService localService; // 本地 JPA 查 activity 表与玩家进度

    /** Feign 远程活动客户端，仅 player-service + remote.enabled=true 时注册 */
    private final ObjectProvider<RemoteActivityClient> remoteClient; // RemoteActivityGateway Feign 实现

    /** game.activity.remote.enabled：true 且 Feign Bean 存在时转发 activity-service */
    private final boolean remoteEnabled; // false 时始终走本地 ActivityService

    public ActivityCommandGateway( // 活动命令网关：默认本地 {@link ActivityService}，player-service 可切换为远程 activity-service
            ActivityService localService, // 本地 JPA 查 activity 表与玩家进度
            ObjectProvider<RemoteActivityClient> remoteClient, // RemoteActivityGateway Feign 实现
            @Value("${game.activity.remote.enabled:false}") boolean remoteEnabled) { // 微服务模式开关，默认本地处理
        this.localService = localService; // 单体部署走本地 ActivityService
        this.remoteClient = remoteClient; // 远程模式走 Feign activity-service
        this.remoteEnabled = remoteEnabled; // false 时始终本地处理
    }

    /** 活动列表 cmd=1：查玩家可见的进行中活动 */
    public ProtocolMessage handleGetActivityList(long playerId, GetActivityListCsReq req) { // 活动列表 cmd=1：查玩家可见的进行中活动
        if (useRemote()) { // game.activity.remote.enabled=true 且 Feign 已注册
            return remoteClient.getObject().handleGetActivityList(playerId, req); // Feign 转发至 activity-service GET_ACTIVITY_LIST
        }
        return localService.handleGetActivityList(playerId, req); // 本地 JPA SELECT activity WHERE opened=true
    }

    /** 活动详情 cmd=3：查单个活动的任务进度与奖励状态 */
    public ProtocolMessage handleGetActivityDetail(long playerId, GetActivityDetailCsReq req) { // 活动详情 cmd=3：查单个活动的任务进度与奖励状态
        if (useRemote()) { // 微服务模式且 Feign 已注册
            return remoteClient.getObject().handleGetActivityDetail(playerId, req); // 远程查 activity 详情与玩家进度
        }
        return localService.handleGetActivityDetail(playerId, req); // 本地查 activity 详情与玩家进度
    }

    /** 领取奖励 cmd=5：校验条件后发放道具/货币 */
    public ProtocolMessage handleClaimActivityReward(long playerId, ClaimActivityRewardCsReq req) { // 领取奖励 cmd=5：校验条件后发放道具/货币
        if (useRemote()) { // 微服务模式且 Feign 已注册
            return remoteClient.getObject().handleClaimActivityReward(playerId, req); // 远程领取并 UPDATE 玩家活动进度
        }
        return localService.handleClaimActivityReward(playerId, req); // 本地领取奖励，经 BagService.grantItemsForActivity 发道具
    }

    /** 是否启用远程路由：开关打开且 Feign 实现已注册 */
    private boolean useRemote() { // 是否启用远程路由：开关打开且 Feign 实现已注册
        return remoteEnabled && remoteClient.getIfAvailable() != null; // 双条件满足才走远端 activity-service
    }
}
