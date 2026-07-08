/**
 * 文件说明：周期性 Buff 工厂类。
 * 职责：根据 BuffConfig.effectType 创建具体的 PeriodicBuff 子类实例（DOT/HOT/通用）。
 */
package cn.itcast.demo.mymmorpg.model;

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 配置实体
import cn.itcast.demo.mymmorpg.support.SchedulerManager; // 定时调度管理器
import cn.itcast.demo.mymmorpg.service.BuffEventPublisher; // Buff 事件发布器
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 解析器
import org.springframework.context.annotation.Lazy; // 延迟注入，打破循环依赖
import org.springframework.stereotype.Component; // Spring 组件

/**
 * 按 {@link BuffConfig#getEffectType()} 创建具体 {@link PeriodicBuff} 子类。
 */
@Component // 注册为 Spring Bean
public class PeriodicBuffFactory { // 周期 Buff 工厂

    /** 效果类型：持续伤害 DOT */
    private static final int EFFECT_DOT = 2; // DOT 效果类型码
    /** 效果类型：持续治疗 HOT */
    private static final int EFFECT_HOT = 3; // HOT 效果类型码

    /** Buff 运行时状态接口 */
    private final PeriodicBuffRuntime buffRuntime; // 运行时状态
    /** 定时任务调度管理器 */
    private final SchedulerManager schedulerManager; // 调度器
    /** 周期 Buff 注册表 */
    private final PeriodicBuffRegistry registry; // 注册表
    /** Buff 事件发布器 */
    private final BuffEventPublisher buffEventPublisher; // 事件发布
    /** JSON 对象映射器 */
    private final ObjectMapper objectMapper; // JSON 解析

    /**
     * 构造器注入依赖（@Lazy 打破循环依赖）。
     *
     * @param buffRuntime         运行时状态接口
     * @param schedulerManager    调度管理器
     * @param registry            注册表
     * @param buffEventPublisher    事件发布器
     * @param objectMapper          JSON 映射器
     */
    public PeriodicBuffFactory(
            @Lazy PeriodicBuffRuntime buffRuntime,
            SchedulerManager schedulerManager,
            @Lazy PeriodicBuffRegistry registry,
            BuffEventPublisher buffEventPublisher,
            ObjectMapper objectMapper) {
        this.buffRuntime = buffRuntime; // 保存运行时接口
        this.schedulerManager = schedulerManager; // 保存调度器
        this.registry = registry; // 保存注册表
        this.buffEventPublisher = buffEventPublisher; // 保存事件发布器
        this.objectMapper = objectMapper; // 保存 JSON 映射器
    }

    /**
     * 根据配置创建对应的 PeriodicBuff 实例。
     *
     * @param entityId 实体 ID
     * @param config   Buff 配置
     * @return 具体 PeriodicBuff 子类实例
     */
    public PeriodicBuff create(long entityId, BuffConfig config) { // 创建周期 Buff
        int et = config.getEffectType() == null ? 0 : config.getEffectType(); // 读取效果类型
        return switch (et) { // 按效果类型分发
            case EFFECT_DOT -> new PeriodicDotBuff( // DOT 持续伤害
                    entityId, config, schedulerManager, buffRuntime, registry, buffEventPublisher, objectMapper);
            case EFFECT_HOT -> new PeriodicHotBuff( // HOT 持续治疗
                    entityId, config, schedulerManager, buffRuntime, registry, buffEventPublisher, objectMapper);
            default -> new GenericPeriodicBuff( // 其他类型通用周期 Buff
                    entityId, config, schedulerManager, buffRuntime, registry, buffEventPublisher);
        };
    }
}
