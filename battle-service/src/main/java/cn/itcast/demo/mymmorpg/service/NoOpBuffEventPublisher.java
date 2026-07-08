/**
 * 文件说明：Buff 事件发布器的空实现（No-Op）。
 * 职责：当 RocketMQ 未启用时，以 debug 日志代替 Buff 领域事件发送。
 * 激活条件：{@code rocketmq.enabled=false} 或未配置时默认启用。
 */
package cn.itcast.demo.mymmorpg.service;

import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按配置属性条件注册 Bean
import org.springframework.stereotype.Component; // 标记为 Spring 组件

/**
 * MQ 关闭时的 Buff 事件空实现：不发送 MQ，仅 debug 日志。
 */
@Component // MQ 关闭时注册
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "false", matchIfMissing = true) // 默认空实现
class NoOpBuffEventPublisher implements BuffEventPublisher { // 不发送 MQ，仅 debug 日志

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(NoOpBuffEventPublisher.class); // 日志记录器

    @Override
    public void publishBuffAdded(long entityId, int buffId, int stackCount) { // Buff 添加事件
        log.debug("MQ 未启用 buffAdd {} {} {}", entityId, buffId, stackCount); // 记录添加日志
    }

    @Override
    public void publishBuffRemoved(long entityId, int buffId, int reason) { // Buff 移除事件
        log.debug("MQ 未启用 buffRemove {} {} {}", entityId, buffId, reason); // 记录移除日志
    }

    @Override
    public void publishBuffUpdated(long entityId, int buffId, long remainingMs, int stackCount) { // Buff 更新事件
        log.debug("MQ 未启用 buffUpdate {} {}", entityId, buffId); // 记录更新日志
    }

    @Override
    public void publishPeriodicSettlement(long entityId, int buffId, int effectType, int amount, String kind) { // 周期结算事件
        log.debug("MQ 未启用 buffPeriodic {} {} amount={} {}", entityId, buffId, amount, kind); // 记录周期结算日志
    }
}
