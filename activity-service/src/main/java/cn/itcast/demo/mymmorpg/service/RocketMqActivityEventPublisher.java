/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/service/RocketMqActivityEventPublisher.java
 * 2) 所属模块：activity-service / service
 * 3) 主要职责：rocketmq.enabled=true 时，将活动领奖事件发送到 RocketMQ
 * 4) 系统位置：事件发布层，与 NoOpActivityEventPublisher 二选一
 * 5) 风险提示：发送失败仅打 warn 日志，不抛异常阻断领奖主流程
 */
package cn.itcast.demo.mymmorpg.service;

import org.apache.rocketmq.client.producer.DefaultMQProducer; // RocketMQ 生产者
import org.apache.rocketmq.common.message.Message; // MQ 消息体
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.beans.factory.annotation.Value; // 注入 topic 配置
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // MQ 开关条件
import org.springframework.stereotype.Component; // Spring 组件

import java.nio.charset.StandardCharsets; // body UTF-8 编码

/**
 * 活动事件 RocketMQ 发布实现。
 */
@Component // 注册 Bean
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true") // 仅 MQ 开启时生效
class RocketMqActivityEventPublisher implements ActivityEventPublisher { // 活动事件 MQ 实现

    /** 类级别日志。 */
    private static final Logger log = LoggerFactory.getLogger(RocketMqActivityEventPublisher.class); // 类日志

    /** 由 RocketMQ 自动配置注入的生产者。 */
    private final DefaultMQProducer producer; // RocketMQ 生产者实例

    /** 活动事件主题，默认 ACTIVITY_EVENTS。 */
    @Value("${activity.mq.topic:ACTIVITY_EVENTS}") // 主题，默认 ACTIVITY_EVENTS
    private String topic; // MQ topic 名称

    /**
     * 包可见构造器，注入 MQ 生产者。
     *
     * @param producer RocketMQ 生产者
     */
    RocketMqActivityEventPublisher(DefaultMQProducer producer) { // 包可见构造器
        this.producer = producer; // 保存生产者引用
    }

    /**
     * 领奖成功后发布 activityReward 事件到 MQ。
     *
     * @param playerId     玩家 ID
     * @param activityId   活动 ID
     * @param activityType 活动类型
     * @param rewardIndex  奖励档位 index（0 表示一键领取）
     * @param claimedCount 本次领取档位数
     */
    @Override
    public void publishRewardClaimed(long playerId, long activityId, int activityType, int rewardIndex, int claimedCount) { // 领奖成功回调
        String body = "activityReward|playerId=" + playerId + "|actId=" + activityId + "|type=" + activityType // 管道符分隔的文本协议
                + "|idx=" + rewardIndex + "|cnt=" + claimedCount; // 拼接档位与数量
        try {
            producer.send(new Message(topic, "claim", body.getBytes(StandardCharsets.UTF_8))); // tag=claim，UTF-8 字节发送
        } catch (Exception e) {
            log.warn("RocketMQ 活动事件发送失败 {}", body, e); // 不抛异常，避免影响领奖
        }
    }
}
