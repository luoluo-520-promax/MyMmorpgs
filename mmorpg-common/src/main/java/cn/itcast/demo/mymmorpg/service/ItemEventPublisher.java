/**
 * 道具变更事件发布抽象：使用/出售/丢弃背包物品后异步通知，
 * 供经济监控、任务进度（「使用 N 次药水」）、运营 BI 订阅。
 */
package cn.itcast.demo.mymmorpg.service;

public interface ItemEventPublisher { // 道具事件对外发布契约：把背包消耗、售卖和丢弃动作送入异步系统

    /**
     * 发布「道具已使用」事件（消耗品、卷轴等）。
     *
     * @param playerId     使用者角色 ID
     * @param itemUid      背包内道具实例唯一 ID
     * @param itemConfigId 道具模板 ID（策划表）
     * @param usedCount    本次使用数量
     */
    void publishItemUsed(long playerId, long itemUid, int itemConfigId, int usedCount); // 记录道具消耗，驱动任务进度与资源回收统计

    /**
     * 发布「道具已出售给 NPC」事件。
     *
     * @param currencyGained 获得的金币数量，供经济流水统计
     */
    void publishItemSold(long playerId, long itemUid, int itemConfigId, int soldCount, long currencyGained); // 记录背包物品变现，便于分析金币产出与市场回流

    /**
     * 发布「道具已丢弃」事件（从背包移除且不可恢复）。
     */
    void publishItemDiscarded(long playerId, long itemUid, int itemConfigId, int count); // 记录玩家主动丢弃物品，供损耗分析与异常行为追踪
}
