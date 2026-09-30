package cn.itcast.demo.mymmorpg.model;

/**
 * 活动奖励履约命令（发往 MQ Outbox，由背包服消费）。
 */
public record ActivityRewardCommand(
        long playerId,
        long activityId,
        String idempotencyKey,
        String itemsCsv) {

    public static String toItemsCsv(int itemId, int count) {
        return itemId + ":" + count;
    }

    public static String appendItem(String csv, int itemId, int count) {
        String token = toItemsCsv(itemId, count);
        if (csv == null || csv.isBlank()) {
            return token;
        }
        return csv + "," + token;
    }
}
