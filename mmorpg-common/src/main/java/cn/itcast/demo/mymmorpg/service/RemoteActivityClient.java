/**
 * 活动服务远程 HTTP 调用端口（Feign 适配层）。
 * player-service 在微服务部署模式下，通过 Feign 实现本接口，将客户端活动协议转发至 activity-service。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一协议响应封装，含 retcode 与 protobuf 载荷
import cn.itcast.demo.mymmorpg.protocol.protobuf.ClaimActivityRewardCsReq; // 领取限时/签到等活动奖励的客户端请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityDetailCsReq; // 查询单个活动详情（规则、进度、奖励档位）
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq; // 查询当前开放活动列表的客户端请求

/**
 * 活动远程调用端口：player-service 在微服务模式下通过 Feign 实现；
 * 单体模式下 ActivityCommandGateway 直接使用本地 ActivityService，不经过 HTTP。
 */
public interface RemoteActivityClient {

    /** 远程查询玩家可见的活动列表，供活动面板展示 */
    ProtocolMessage handleGetActivityList(long playerId, GetActivityListCsReq req); // 拉取可参与活动，驱动活动页展示和入口红点

    /** 远程查询指定活动的详细规则与玩家进度 */
    ProtocolMessage handleGetActivityDetail(long playerId, GetActivityDetailCsReq req); // 获取活动规则、奖励档位与个人完成度

    /** 远程领取活动奖励（如签到宝箱、任务档位奖励），触发发奖逻辑 */
    ProtocolMessage handleClaimActivityReward(long playerId, ClaimActivityRewardCsReq req); // 校验领奖条件并发放对应活动奖励
}
