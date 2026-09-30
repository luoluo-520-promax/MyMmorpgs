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
 * 订阅 SHOP_EVENTS / paid，推进 SHOP_PAY 类任务。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class ShopOrderPaidMqConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShopOrderPaidMqConsumer.class);

    private final QuestShopPayProgressService shopPayProgressService;
    private final String nameServer;
    private final String topic;
    private final String group;
    private DefaultMQPushConsumer consumer;

    public ShopOrderPaidMqConsumer(QuestShopPayProgressService shopPayProgressService,
                                   @Value("${rocketmq.name-server:127.0.0.1:9876}") String nameServer,
                                   @Value("${shop.mq.topic:SHOP_EVENTS}") String topic,
                                   @Value("${shop.mq.quest-consumer-group:quest-shop-consumer}") String group) {
        this.shopPayProgressService = shopPayProgressService;
        this.nameServer = nameServer;
        this.topic = topic;
        this.group = group;
    }

    @PostConstruct
    public void start() throws Exception {
        consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(topic, "paid");
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                handle(new String(msg.getBody(), StandardCharsets.UTF_8));
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("Quest ShopOrderPaid MQ consumer started topic={} group={}", topic, group);
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    void handle(String body) {
        Map<String, String> fields = parse(body);
        long playerId = parseLong(fields.get("playerId"));
        long amount = parseLong(fields.get("amount"));
        String orderId = fields.getOrDefault("orderId", "");
        if (playerId <= 0 || orderId.isBlank()) {
            log.warn("Skip invalid ShopOrderPaid body={}", body);
            return;
        }
        shopPayProgressService.applyShopPaid(playerId, orderId, amount);
    }

    private static Map<String, String> parse(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) {
            return map;
        }
        for (String part : body.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                map.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return map;
    }

    private static long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
