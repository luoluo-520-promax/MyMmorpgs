/**
 * 文件说明：战斗事件 RocketMQ 发布器实现。
 * 职责：将战斗开始/结束事件序列化为文本消息并发送到指定 MQ 主题。
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
 * RocketMQ 战斗事件发布器：将战斗生命周期事件发送到 MQ 主题。
 */
@Component // MQ 开启时注册
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true") // 仅当配置为 true 时生效
class RocketMqBattleEventPublisher implements BattleEventPublisher { // 战斗事件 MQ 实现

    /** 类级别日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(RocketMqBattleEventPublisher.class); // 类日志

    /** 注入的 RocketMQ 生产者 */
    private final DefaultMQProducer producer; // 注入的生产者

    /** 战斗事件 MQ 主题名 */
    @Value("${battle.mq.topic:BATTLE_EVENTS}") // 主题配置，默认 BATTLE_EVENTS
    private String topic; // MQ 主题

    /**
     * 构造器注入 MQ 生产者。
     *
     * @param producer RocketMQ 生产者实例
     */
    RocketMqBattleEventPublisher(DefaultMQProducer producer) { // 构造器注入
        this.producer = producer; // 保存生产者引用
    }

    @Override
    public void publishBattleStarted(long playerId, long battleId, int sceneId, long enemyEntityId, int monsterTemplateId) { // 战斗开始
        send("start", "battleStart|playerId=" + playerId + "|battleId=" + battleId + "|sceneId=" + sceneId
                + "|enemyEntityId=" + enemyEntityId + "|templateId=" + monsterTemplateId); // 组装并发送开始消息
    }

    @Override
    public void publishBattleEnded(long playerId, long battleId, int result, int expReward, int durationSec) { // 战斗结束
        send("end", "battleEnd|playerId=" + playerId + "|battleId=" + battleId + "|result=" + result
                + "|exp=" + expReward + "|duration=" + durationSec); // 组装并发送结束消息
    }

    /**
     * 统一发送 MQ 消息的内部方法。
     *
     * @param tags 消息标签
     * @param body 消息体文本
     */
    private void send(String tags, String body) { // 统一发送
        try { // 捕获发送异常
            Message msg = new Message(topic, tags, body.getBytes(StandardCharsets.UTF_8)); // 构造 UTF-8 消息
            producer.send(msg); // 同步发送
        } catch (Exception e) { // 发送失败
            log.warn("RocketMQ 战斗事件发送失败 {}", body, e); // 记录警告，不抛异常
        }
    }
}
