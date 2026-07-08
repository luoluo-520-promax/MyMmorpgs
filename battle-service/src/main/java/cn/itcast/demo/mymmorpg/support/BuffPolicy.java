/**
 * 文件说明：Buff 规则扩展策略接口。
 * 职责：抽象 Buff 施加与移除的权限校验逻辑，支持 Groovy 脚本热替换实现。
 * 默认实现：{@link cn.itcast.demo.mymmorpg.config.BattlePolicyConfiguration}。
 */
package cn.itcast.demo.mymmorpg.support;

/**
 * Buff 规则扩展（Groovy 实现）：是否可施加、是否可驱散等。
 */
public interface BuffPolicy { // Buff 权限与规则策略

    /**
     * 判断是否允许为实体施加指定 Buff。
     *
     * @param entityId 目标实体 ID
     * @param buffId   Buff 模板 ID
     * @return 是否允许施加
     */
    default boolean canApplyBuff(long entityId, int buffId) { // 是否可施加 Buff
        return true; // 默认允许
    }

    /**
     * 判断是否允许移除指定实体上的 Buff。
     *
     * @param requesterPlayerId 发起移除的玩家（来自会话）
     * @param targetEntityId    承受 Buff 的实体
     * @param buffId            Buff 模板 ID
     * @return 是否允许移除
     */
    default boolean canRemoveBuff(long requesterPlayerId, long targetEntityId, int buffId) { // 是否可移除 Buff
        return true; // 默认允许
    }
}
