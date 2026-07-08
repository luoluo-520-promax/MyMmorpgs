/**
 * 文件说明：周期性 Buff 运行时状态刷新接口。
 * 职责：供 PeriodicBuff 定时任务从持久化状态刷新层数并校验 Buff 是否仍存在，避免与 BuffService 循环依赖。
 */
package cn.itcast.demo.mymmorpg.model;

/**
 * 供 {@link PeriodicBuff} 从持久化状态刷新层数、校验 Buff 是否仍存在（避免与 {@link cn.itcast.demo.mymmorpg.service.BuffService} 循环依赖）。
 */
public interface PeriodicBuffRuntime { // 周期 Buff 运行时状态访问接口

    /**
     * 从 Redis 等加载当前 Buff；若已不存在或已过期则返回 false，周期任务应停止。
     *
     * @param entityId      实体 ID
     * @param buffId        Buff 模板 ID
     * @param periodicBuff  周期 Buff 实例（用于回写层数）
     * @return Buff 是否仍有效
     */
    boolean refreshStateForPeriodicTick(long entityId, int buffId, PeriodicBuff periodicBuff); // 刷新周期结算前的状态
}
