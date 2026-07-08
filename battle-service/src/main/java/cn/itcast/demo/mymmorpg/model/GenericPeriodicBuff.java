/**
 * 文件说明：通用周期性 Buff 实现。
 * 职责：处理其他配置了 periodic_interval 的效果类型，仅上报周期心跳事件，便于后续扩展。
 */
package cn.itcast.demo.mymmorpg.model;

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 配置
import cn.itcast.demo.mymmorpg.support.SchedulerManager; // 调度管理器
import cn.itcast.demo.mymmorpg.service.BuffEventPublisher; // 事件发布器
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂

/**
 * 其它配置了 periodic_interval 的效果类型：仅上报周期心跳，便于扩展。
 */
public class GenericPeriodicBuff extends PeriodicBuff { // 通用周期 Buff 实现

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(GenericPeriodicBuff.class); // 日志

    /** Buff 事件发布器 */
    private final BuffEventPublisher buffEventPublisher; // 事件发布

    /**
     * 构造通用周期 Buff。
     *
     * @param entityId           实体 ID
     * @param config             Buff 配置
     * @param schedulerManager   调度管理器
     * @param buffRuntime        运行时状态接口
     * @param registry           注册表
     * @param buffEventPublisher 事件发布器
     */
    public GenericPeriodicBuff(
            long entityId,
            BuffConfig config,
            SchedulerManager schedulerManager,
            PeriodicBuffRuntime buffRuntime,
            PeriodicBuffRegistry registry,
            BuffEventPublisher buffEventPublisher) {
        super(entityId, config, schedulerManager, buffRuntime, registry); // 调用父类构造
        this.buffEventPublisher = buffEventPublisher; // 保存事件发布器
    }

    @Override
    protected void enterFrame() { // 周期结算入口
        int et = config.getEffectType() == null ? 0 : config.getEffectType(); // 读取效果类型
        buffEventPublisher.publishPeriodicSettlement(entityId, buffId, et, 0, "generic"); // 发布通用心跳事件
        log.trace("周期性 Buff tick entityId={} buffId={} effectType={}", entityId, buffId, et); // 跟踪日志
    }
}
