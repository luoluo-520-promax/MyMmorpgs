/**
 * 聊天事件 RocketMQ 发布实现：玩家发送聊天成功后投递 CHAT_EVENTS Topic，
 * 供聊天记录持久化、敏感词二次审核、跨服聊天网关转发。
 */
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
class RocketMqChatEventPublisher implements ChatEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqChatEventPublisher.class);

    private final DefaultMQProducer producer;

    @Value("${chat.mq.topic:CHAT_EVENTS}")
    private String topic;

    RocketMqChatEventPublisher(DefaultMQProducer producer) {
        this.producer = producer;
    }

    @Override
    public void publishChatSent(long senderId, int channel, long targetId, int msgType, String content, long serverTs) {
        try {
            // 拼接频道、目标、类型、时间戳与正文；消费端可按需解析、入库或转发到其他服
            String body = "chat|sender=" + senderId + "|ch=" + channel + "|tgt=" + targetId + "|type=" + msgType
                    + "|ts=" + serverTs + "|content=" + content;
            Message msg = new Message(topic, "send", body.getBytes(StandardCharsets.UTF_8)); // tag=send 表示 outbound 聊天
            producer.send(msg); // 同步发送；聊天已成功入库/广播给在线玩家，MQ 失败不回滚
        } catch (Exception e) {
            log.warn("RocketMQ 聊天事件发送失败 sender={}", senderId, e);
        }
    }
}
