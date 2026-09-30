package cn.itcast.demo.mymmorpg.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 订阅 SOCIAL_EVENTS，驱动社交成就计数。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class SocialEventMqConsumer {

    private static final Logger log = LoggerFactory.getLogger(SocialEventMqConsumer.class);

    private final SocialAchievementService socialAchievementService;
    private final String nameServer;
    private final String topic;
    private final String group;
    private DefaultMQPushConsumer consumer;

    public SocialEventMqConsumer(SocialAchievementService socialAchievementService,
                                 @Value("${rocketmq.name-server:127.0.0.1:9876}") String nameServer,
                                 @Value("${social.mq.topic:SOCIAL_EVENTS}") String topic,
                                 @Value("${social.mq.consumer-group:social-event-consumer}") String group) {
        this.socialAchievementService = socialAchievementService;
        this.nameServer = nameServer;
        this.topic = topic;
        this.group = group;
    }

    @PostConstruct
    public void start() throws Exception {
        consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(topic, "*");
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                handle(new String(msg.getBody(), StandardCharsets.UTF_8));
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("SocialEvent MQ consumer started topic={} group={}", topic, group);
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    void handle(String body) {
        Map<String, String> fields = parse(body);
        String type = fields.getOrDefault("type", "");
        long actor = parseLong(fields.get("actor"));
        long target = parseLong(fields.get("target"));
        if (actor <= 0) {
            log.warn("Skip invalid social event body={}", body);
            return;
        }
        socialAchievementService.applySocialEvent(type, actor, target);
    }

    private static Map<String, String> parse(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null) {
            return map;
        }
        for (String part : body.split("\\|")) {
            int i = part.indexOf('=');
            if (i > 0) {
                map.put(part.substring(0, i), part.substring(i + 1));
            }
        }
        return map;
    }

    private static long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
