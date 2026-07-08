/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/support/ActivityPolicy.java
 * 2) 所属模块：activity-service / support
 * 3) 主要职责：可扩展的活动领奖校验策略接口（Groovy/Java 实现均可）
 * 4) 系统位置：支撑层，被 ActivityService 在 canClaim 流程中调用
 * 5) 变更建议：扩展策略时实现本接口并注册为 Spring Bean 覆盖默认实现
 */
package cn.itcast.demo.mymmorpg.support;

import java.util.Map; // 策略上下文键值对

/**
 * Groovy/Java 可扩展的活动领取校验策略。
 */
public interface ActivityPolicy { // 活动领奖策略接口

    /**
     * 判断是否允许领取指定档位奖励。
     *
     * @param activityType 活动类型编码
     * @param activityId   活动实例 ID
     * @param playerId     玩家 ID
     * @param rewardIndex  奖励档位 index
     * @param ctx          上下文（含 tier、progress 等）
     * @return true 表示允许领取
     */
    default boolean allowClaimReward(int activityType, long activityId, long playerId, int rewardIndex, Map<String, Object> ctx) {
        return true; // 默认实现：不做额外限制，全部允许
    }
}
