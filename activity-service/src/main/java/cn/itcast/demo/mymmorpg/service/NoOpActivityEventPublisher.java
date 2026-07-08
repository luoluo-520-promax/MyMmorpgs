/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/service/NoOpActivityEventPublisher.java
 * 2) 所属模块：activity-service / service
 * 3) 主要职责：RocketMQ 未启用时的 ActivityEventPublisher 空实现
 * 4) 系统位置：事件发布层，默认 matchIfMissing=true 时生效
 * 5) 变更建议：本地开发无需 MQ 时保持 rocketmq.enabled=false 即可
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按配置条件注册
import org.springframework.stereotype.Component; // Spring 组件

/**
 * MQ 未启用时的活动事件空发布器。
 */
@Component // 默认 Bean
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true) // 未配置 MQ 时用此类
class NoOpActivityEventPublisher implements ActivityEventPublisher { // 空实现，不发送消息

    /** 类级别 debug 日志。 */
    private static final Logger log = LoggerFactory.getLogger(NoOpActivityEventPublisher.class); // debug 日志

    /**
     * 领奖事件回调（不实际发送 MQ）。
     *
     * @param playerId     玩家 ID
     * @param activityId   活动 ID
     * @param activityType 活动类型
     * @param rewardIndex  奖励档位 index
     * @param claimedCount 本次领取档位数
     */
    @Override
    public void publishRewardClaimed(long playerId, long activityId, int activityType, int rewardIndex, int claimedCount) { // 领奖事件（不发送）
        log.debug("MQ 未启用 activityReward playerId={} activityId={}", playerId, activityId); // 开发环境可观测
    }
}
