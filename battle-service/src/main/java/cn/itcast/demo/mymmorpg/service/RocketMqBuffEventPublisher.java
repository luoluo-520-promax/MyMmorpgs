/**
 * 文件说明：Buff 事件 RocketMQ 发布器实现。
 * 职责：将 Buff 增删改及周期性结算事件发送到指定 MQ 主题。
 * 激活条件：{@code rocketmq.enabled=true}。
 */
package cn.itcast.demo.mymmorpg.service;

import org.apache.rocketmq.client.producer.DefaultMQProducer; // MQ 生产者
import org.apache.rocketmq.common.message.Message; // MQ 消息体
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.beans.factory.annotation.Value; // 注入配置属性
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按配置条件注册
import org.springframework.stereotype.Component; // Spring 组件

import java.nio.charset.StandardCharsets; // UTF-8 字符集

/**
 * RocketMQ Buff 事件发布器：将 Buff 领域事件发送到 MQ 主题。
 */
@Component // MQ 开启时注册
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true") // MQ 开启时生效
class RocketMqBuffEventPublisher implements BuffEventPublisher { // Buff 领域事件 MQ 实现

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(RocketMqBuffEventPublisher.class); // 日志记录器

    /** 注入的 RocketMQ 生产者 */
    private final DefaultMQProducer producer; // 注入的生产者

    /** Buff 事件 MQ 主题名 */
    @Value("${buff.mq.topic:BUFF_EVENTS}") // 默认主题 BUFF_EVENTS
    private String topic; // MQ 主题

    /**
     * 构造器注入 MQ 生产者。
     *
     * @param producer RocketMQ 生产者实例
     */
    RocketMqBuffEventPublisher(DefaultMQProducer producer) { // 构造器注入
        this.producer = producer; // 保存生产者引用
    }

    @Override
    public void publishBuffAdded(long entityId, int buffId, int stackCount) { // Buff 添加
        send("add", "buffAdd|entity=" + entityId + "|buffId=" + buffId + "|stack=" + stackCount); // 发送添加消息
    }

    @Override
    public void publishBuffRemoved(long entityId, int buffId, int reason) { // Buff 移除
        send("remove", "buffRemove|entity=" + entityId + "|buffId=" + buffId + "|reason=" + reason); // 发送移除消息
    }

    @Override
    public void publishBuffUpdated(long entityId, int buffId, long remainingMs, int stackCount) { // 层数/剩余时间更新
        send("update", "buffUpdate|entity=" + entityId + "|buffId=" + buffId + "|remainMs=" + remainingMs
                + "|stack=" + stackCount); // 发送更新消息
    }

    @Override
    public void publishPeriodicSettlement(long entityId, int buffId, int effectType, int amount, String kind) { // DOT/HOT 周期结算
        send("periodic", "buffPeriodic|entity=" + entityId + "|buffId=" + buffId + "|effectType=" + effectType
                + "|amount=" + amount + "|kind=" + kind); // 发送周期结算消息
    }

    /**
     * 统一发送 MQ 消息的内部方法。
     *
     * @param tags 消息标签
     * @param body 消息体文本
     */
    private void send(String tags, String body) { // 统一发送
        try { // 捕获发送异常
            Message msg = new Message(topic, tags, body.getBytes(StandardCharsets.UTF_8)); // 构造消息
            producer.send(msg); // 同步发送
        } catch (Exception e) { // 发送失败
            log.warn("RocketMQ Buff 事件发送失败 {}", body, e); // 失败不阻塞 Buff 主逻辑
        }
    }
}
