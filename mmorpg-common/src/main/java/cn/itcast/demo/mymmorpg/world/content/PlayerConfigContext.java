package cn.itcast.demo.mymmorpg.world.content;

/**
 * 场景加载配置时的玩家上下文，用于灰度匹配。
 */
public record PlayerConfigContext(
        long playerId,
        long accountId,
        int zoneId,
        boolean betaTester) {
}
