package cn.itcast.demo.mymmorpg.tlog;

import java.util.Map;

/**
 * 玩家行为日志（TLog）发布口：异步投递至 Kafka / ES / 本地日志。
 */
public interface TLogEventPublisher {

    /**
     * @param eventType 事件名，如 battle_end / shop_purchase / gacha_draw
     * @param playerId  玩家 ID（0 表示系统）
     * @param fields    业务字段
     */
    void emit(String eventType, long playerId, Map<String, Object> fields);
}
