/**
 * 文件说明：持续治疗（HOT）周期性 Buff 实现。
 * 职责：从 effect_params 读取 heal_per_tick，按层数放大后发布周期结算事件。
 */
package cn.itcast.demo.mymmorpg.model;

import cn.itcast.demo.mymmorpg.entity.BuffConfig; // Buff 配置
import cn.itcast.demo.mymmorpg.support.SchedulerManager; // 调度管理器
import cn.itcast.demo.mymmorpg.service.BuffEventPublisher; // 事件发布器
import com.fasterxml.jackson.databind.JsonNode; // JSON 节点
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 映射器
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂

/**
 * 持续治疗（effect_type=3）：从 effect_params 读取 heal_per_tick。
 */
public class PeriodicHotBuff extends PeriodicBuff { // HOT 周期 Buff 实现

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(PeriodicHotBuff.class); // 日志

    /** Buff 事件发布器 */
    private final BuffEventPublisher buffEventPublisher; // 事件发布
    /** JSON 对象映射器 */
    private final ObjectMapper objectMapper; // JSON 解析

    /**
     * 构造 HOT 周期 Buff。
     *
     * @param entityId           实体 ID
     * @param config             Buff 配置
     * @param schedulerManager   调度管理器
     * @param buffRuntime        运行时状态接口
     * @param registry           注册表
     * @param buffEventPublisher 事件发布器
     * @param objectMapper       JSON 映射器
     */
    public PeriodicHotBuff(
            long entityId,
            BuffConfig config,
            SchedulerManager schedulerManager,
            PeriodicBuffRuntime buffRuntime,
            PeriodicBuffRegistry registry,
            BuffEventPublisher buffEventPublisher,
            ObjectMapper objectMapper) {
        super(entityId, config, schedulerManager, buffRuntime, registry); // 调用父类构造
        this.buffEventPublisher = buffEventPublisher; // 保存事件发布器
        this.objectMapper = objectMapper; // 保存 JSON 映射器
    }

    @Override
    protected void enterFrame() { // 周期结算入口
        int perTick = readIntParam("heal_per_tick", 0); // 读取每 tick 治疗量
        int total = perTick * stackCount; // 按层数放大
        if (total > 0) { // 有有效治疗
            buffEventPublisher.publishPeriodicSettlement(entityId, buffId, 3, total, "hot"); // 发布 HOT 结算事件
            log.debug("HOT 结算 entityId={} buffId={} amount={}", entityId, buffId, total); // 调试日志
        }
    }

    /**
     * 从 effect_params JSON 中读取整型参数。
     *
     * @param field 字段名
     * @param def   默认值
     * @return 参数值
     */
    private int readIntParam(String field, int def) { // 读取 JSON 整型参数
        String p = config.getEffectParams(); // 获取效果参数字符串
        if (p == null || p.isBlank()) { // 无参数
            return def; // 返回默认值
        }
        try { // 解析 JSON
            JsonNode n = objectMapper.readTree(p); // 解析为 JSON 树
            if (n.has(field)) { // 字段存在
                return n.get(field).asInt(def); // 读取整型值
            }
        } catch (Exception e) { // 解析失败
            log.debug("解析 effect_params 失败 buffId={}", buffId, e); // 记录调试日志
        }
        return def; // 返回默认值
    }
}
