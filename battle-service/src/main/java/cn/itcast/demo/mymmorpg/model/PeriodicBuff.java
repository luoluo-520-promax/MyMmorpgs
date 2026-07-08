/**
 * 文件说明：周期性 Buff 抽象基类。
 * 职责：在 registerFrameTask() 中向 SchedulerManager 注册定时任务，每个实例持有 periodicTask；
 *       结算逻辑在 enterFrame() 中由子类实现。
 */
package cn.itcast.demo.mymmorpg.model;

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 配置实体
import cn.itcast.demo.mymmorpg.support.SchedulerManager; // 定时任务调度管理器
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂

import java.util.concurrent.ScheduledFuture; // 定时任务 Future
import java.util.concurrent.atomic.AtomicBoolean; // 原子布尔标志

/**
 * 周期性 Buff 抽象：在 {@link #registerFrameTask()} 中向 {@link SchedulerManager} 注册定时任务；
 * 每个实例持有 {@link #periodicTask}；结算在 {@link #enterFrame()} 中执行。
 */
public abstract class PeriodicBuff { // 周期性 Buff 抽象基类

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(PeriodicBuff.class); // 日志

    /** 承受 Buff 的实体 ID */
    protected final long entityId; // 实体 ID
    /** Buff 模板 ID */
    protected final int buffId; // Buff ID
    /** Buff 配置 */
    protected final BuffConfig config; // 配置
    /** 周期间隔毫秒数 */
    protected final long periodicIntervalMs; // 周期间隔
    /** 定时任务调度管理器 */
    protected final SchedulerManager schedulerManager; // 调度器
    /** Buff 运行时状态访问接口 */
    protected final PeriodicBuffRuntime buffRuntime; // 运行时状态
    /** 周期 Buff 注册表 */
    protected final PeriodicBuffRegistry registry; // 注册表

    /**
     * 当前周期任务 Future，取消即停止后续 tick（Buff 销毁时）。
     */
    protected volatile ScheduledFuture<?> periodicTask; // 当前定时任务

    /** 当前层数 */
    protected volatile int stackCount; // Buff 层数

    /** 是否已销毁标志 */
    private final AtomicBoolean destroyed = new AtomicBoolean(false); // 销毁标志
    /** 是否已注册首帧任务标志 */
    private final AtomicBoolean frameRegistered = new AtomicBoolean(false); // 注册标志

    /**
     * 构造周期性 Buff 实例。
     *
     * @param entityId          实体 ID
     * @param config            Buff 配置
     * @param schedulerManager  调度管理器
     * @param buffRuntime       运行时状态接口
     * @param registry          注册表
     */
    protected PeriodicBuff(
            long entityId,
            BuffConfig config,
            SchedulerManager schedulerManager,
            PeriodicBuffRuntime buffRuntime,
            PeriodicBuffRegistry registry) {
        this.entityId = entityId; // 保存实体 ID
        this.config = config; // 保存配置
        this.buffId = config.getId(); // 从配置取 Buff ID
        Integer interval = config.getPeriodicInterval(); // 读取周期间隔
        this.periodicIntervalMs = interval == null || interval <= 0 ? 0L : interval.longValue(); // 无效间隔视为 0
        this.schedulerManager = schedulerManager; // 保存调度器
        this.buffRuntime = buffRuntime; // 保存运行时接口
        this.registry = registry; // 保存注册表
    }

    /**
     * 初始化时注册首帧延迟任务；同一实例只注册一次。
     */
    public void registerFrameTask() { // 注册周期任务
        if (!frameRegistered.compareAndSet(false, true)) { // CAS 保证只注册一次
            return; // 已注册则跳过
        }
        if (periodicIntervalMs <= 0) { // 无有效间隔
            return; // 不注册定时任务
        }
        scheduleNextFrame(); // 调度首帧
    }

    /**
     * 调度下一帧周期任务。
     */
    private void scheduleNextFrame() { // 调度下一帧
        if (destroyed.get()) { // 已销毁
            return; // 不再调度
        }
        periodicTask = schedulerManager.schedule(this::onFrameWrapped, periodicIntervalMs); // 延迟调度
    }

    /**
     * 定时器线程入口：先校验 Buff 仍存在，再执行子类结算；异常不中断后续周期（在 finally 重新 schedule）。
     */
    private void onFrameWrapped() { // 定时器回调包装
        if (destroyed.get()) { // 已销毁
            return; // 直接返回
        }
        try { // 刷新状态
            if (!buffRuntime.refreshStateForPeriodicTick(entityId, buffId, this)) { // Buff 已不存在
                destroy(); // 销毁自身
                registry.remove(entityId, buffId); // 从注册表移除
                return; // 结束本次 tick
            }
            try { // 执行结算
                enterFrame(); // 子类实现具体结算
            } catch (Throwable t) { // 结算异常
                log.error("enterFrame 结算异常 entityId={} buffId={}", entityId, buffId, t); // 记录错误
            }
        } catch (Throwable t) { // 刷新状态异常
            log.error("refreshState 异常 entityId={} buffId={}", entityId, buffId, t); // 记录错误
        } finally { // 无论成败
            if (!destroyed.get()) { // 未销毁
                scheduleNextFrame(); // 继续调度下一帧
            }
        }
    }

    /**
     * 子类实现：周期性伤害/治疗等真实结算。
     */
    protected abstract void enterFrame(); // 周期结算入口

    /**
     * 设置 Buff 层数。
     *
     * @param stackCount 层数
     */
    public void setStackCount(int stackCount) { // 设置层数
        this.stackCount = Math.max(1, stackCount); // 至少 1 层
    }

    /**
     * 获取当前层数。
     *
     * @return 层数
     */
    public int getStackCount() { // 获取层数
        return stackCount; // 返回当前层数
    }

    /**
     * Buff 从实体上移除时调用：取消定时任务，不再调度。
     */
    public void destroy() { // 销毁周期任务
        if (!destroyed.compareAndSet(false, true)) { // CAS 保证只销毁一次
            return; // 已销毁
        }
        ScheduledFuture<?> f = periodicTask; // 读取当前任务
        if (f != null) { // 任务存在
            f.cancel(false); // 取消定时任务
        }
    }

    /**
     * 是否已销毁。
     *
     * @return 销毁状态
     */
    public boolean isDestroyed() { // 查询销毁状态
        return destroyed.get(); // 返回标志
    }
}
