/**
 * 活动领域事件发布抽象：玩家领取活动奖励后向外广播，
 * 供运营统计、成就系统、RocketMQ 下游消费者订阅（rocketmq.enabled 切换实现）。
 */
package cn.itcast.demo.mymmorpg.service; // 事件发布接口包，与 RocketMq* / NoOp* 实现类同包

public interface ActivityEventPublisher { // 活动奖励领取后的异步通知端口

    /**
     * 发布「活动奖励已领取」事件。
     *
     * @param playerId     领取奖励的角色 ID
     * @param activityId   活动实例 ID（如某期签到活动）
     * @param activityType 活动类型枚举值（签到/限时任务等）
     * @param rewardIndex  奖励档位索引（第几天/第几档）
     * @param claimedCount 累计领取次数，供风控与统计
     */
    void publishRewardClaimed(long playerId, long activityId, int activityType, int rewardIndex, int claimedCount); // 签到/任务领奖成功后调用，触发 MQ 或成就计数
}
