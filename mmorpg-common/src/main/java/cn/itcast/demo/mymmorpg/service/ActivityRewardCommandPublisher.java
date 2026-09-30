package cn.itcast.demo.mymmorpg.service;

/**
 * 活动奖励履约命令发布（ACTIVITY_EVENTS / reward_grant）。
 */
public interface ActivityRewardCommandPublisher {

    void publish(long playerId, long activityId, String idempotencyKey, String itemsCsv);
}
