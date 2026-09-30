/**
 * 社交事件 RocketMQ 发布：投递 SOCIAL_EVENTS Topic，供社交成就、活动、任务消费。
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
class RocketMqSocialEventPublisher implements SocialEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqSocialEventPublisher.class);

    private final DefaultMQProducer producer;

    @Value("${social.mq.topic:SOCIAL_EVENTS}")
    private String topic;

    RocketMqSocialEventPublisher(DefaultMQProducer producer) {
        this.producer = producer;
    }

    @Override
    public void publishFriendOnline(long playerId) {
        send("FRIEND_ONLINE", "friend_online", playerId, 0L, "playerId=" + playerId);
    }

    @Override
    public void publishPartyFormed(long leaderId, String partyId, int memberCount) {
        send("PARTY_FORMED", "party_formed", leaderId, 0L,
                "partyId=" + nullSafe(partyId) + "|members=" + memberCount);
    }

    @Override
    public void publishAssistSettled(long borrowerId, long ownerId, boolean victory) {
        send("ASSIST_SETTLED", "assist_settled", borrowerId, ownerId, "victory=" + victory);
    }

    @Override
    public void publishHomeVisited(long visitorId, long ownerId) {
        send("HOME_VISITED", "home_visited", visitorId, ownerId, "");
    }

    @Override
    public void publishCoopInteraction(String roomId, String action, long actorId, long targetId) {
        send("COOP_" + nullSafe(action).toUpperCase(), "coop", actorId, targetId,
                "roomId=" + nullSafe(roomId) + "|action=" + nullSafe(action));
    }

    @Override
    public void publish(String eventType, long actorId, long targetId, String payload) {
        String type = eventType == null || eventType.isBlank() ? "GENERIC" : eventType.trim().toUpperCase();
        send(type, "generic", actorId, targetId, payload == null ? "" : payload);
    }

    private void send(String eventType, String tag, long actorId, long targetId, String payload) {
        try {
            String body = "social|type=" + eventType + "|actor=" + actorId + "|target=" + targetId
                    + "|ts=" + System.currentTimeMillis() + "|payload=" + payload;
            Message msg = new Message(topic, tag, body.getBytes(StandardCharsets.UTF_8));
            producer.send(msg);
        } catch (Exception e) {
            log.warn("RocketMQ 社交事件发送失败 type={} actor={}", eventType, actorId, e);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
