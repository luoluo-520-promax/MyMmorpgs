package cn.itcast.demo.mymmorpg.port;

/**
 * 活动命令端口：player 侧路由到 activity-service。
 */
public interface ActivityCommandPort {

    byte[] claimReward(long playerId, byte[] requestPayload);

    byte[] getProgress(long playerId, byte[] requestPayload);
}
