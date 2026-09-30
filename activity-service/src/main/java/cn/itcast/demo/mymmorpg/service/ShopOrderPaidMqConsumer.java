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
 * 订阅 SHOP_EVENTS / paid，将实付金额累加到开启中的首充活动。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class ShopOrderPaidMqConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShopOrderPaidMqConsumer.class);

    private final ActivityShopRechargeService rechargeService;
    private final String nameServer;
    private final String topic;
    private final String group;
    private DefaultMQPushConsumer consumer;

    public ShopOrderPaidMqConsumer(ActivityShopRechargeService rechargeService,
                                   @Value("${rocketmq.name-server:127.0.0.1:9876}") String nameServer,
                                   @Value("${shop.mq.topic:SHOP_EVENTS}") String topic,
                                   @Value("${shop.mq.consumer-group:activity-shop-consumer}") String group) {
        this.rechargeService = rechargeService;
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
        log.info("ShopOrderPaid MQ consumer started topic={} group={}", topic, group);
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
        if (playerId <= 0 || amount <= 0) {
            log.warn("Skip invalid ShopOrderPaid body={}", body);
            return;
        }
        rechargeService.applyRecharge(playerId, amount, orderId);
    }

    private static Map<String, String> parse(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) {
            return map;
        }
        String[] parts = body.split("\\|");
        for (String part : parts) {
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
