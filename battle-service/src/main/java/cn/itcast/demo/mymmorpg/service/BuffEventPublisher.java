/**
 * 文件说明：Buff 领域事件发布器接口定义。
 * 职责：抽象 Buff 增删改及周期性结算事件的发布能力。
 * 实现类：{@link RocketMqBuffEventPublisher}、{@link NoOpBuffEventPublisher}。
 */
package cn.itcast.demo.mymmorpg.service;

/**
 * Buff 增删改事件（RocketMQ）发布接口。
 */
public interface BuffEventPublisher { // Buff 事件发布抽象接口

    /**
     * 发布 Buff 添加事件。
     *
     * @param entityId   实体 ID
     * @param buffId     Buff 模板 ID
     * @param stackCount 层数
     */
    void publishBuffAdded(long entityId, int buffId, int stackCount); // Buff 添加事件

    /**
     * 发布 Buff 移除事件。
     *
     * @param entityId 实体 ID
     * @param buffId   Buff 模板 ID
     * @param reason   移除原因码
     */
    void publishBuffRemoved(long entityId, int buffId, int reason); // Buff 移除事件

    /**
     * 发布 Buff 更新事件（层数或剩余时间变更）。
     *
     * @param entityId     实体 ID
     * @param buffId       Buff 模板 ID
     * @param remainingMs  剩余毫秒数
     * @param stackCount   层数
     */
    void publishBuffUpdated(long entityId, int buffId, long remainingMs, int stackCount); // Buff 更新事件

    /**
     * 周期性结算（DOT/HOT 等），amount 为本次结算数值（伤害为正、治疗为正）。
     *
     * @param entityId   实体 ID
     * @param buffId     Buff 模板 ID
     * @param effectType 效果类型
     * @param amount     结算数值
     * @param kind       结算种类标识（dot/hot/generic）
     */
    void publishPeriodicSettlement(long entityId, int buffId, int effectType, int amount, String kind); // 周期结算事件
}
