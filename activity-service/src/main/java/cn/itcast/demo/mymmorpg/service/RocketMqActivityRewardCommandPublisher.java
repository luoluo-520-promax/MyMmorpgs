package cn.itcast.demo.mymmorpg.service;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class RocketMqActivityRewardCommandPublisher implements ActivityRewardCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqActivityRewardCommandPublisher.class);

    private final DefaultMQProducer producer;
    private final String topic;

    public RocketMqActivityRewardCommandPublisher(
            DefaultMQProducer producer,
            @Value("${activity.mq.topic:ACTIVITY_EVENTS}") String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    public void publish(long playerId, long activityId, String idempotencyKey, String itemsCsv) {
        String body = "activityRewardCmd|playerId=" + playerId
                + "|activityId=" + activityId
                + "|idempotencyKey=" + (idempotencyKey == null ? "" : idempotencyKey)
                + "|items=" + (itemsCsv == null ? "" : itemsCsv);
        try {
            producer.send(new Message(topic, "reward_grant", body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("RocketMQ ActivityRewardCommand 发送失败 {}", body, e);
            throw new IllegalStateException("publish activity reward failed", e);
        }
    }
}
