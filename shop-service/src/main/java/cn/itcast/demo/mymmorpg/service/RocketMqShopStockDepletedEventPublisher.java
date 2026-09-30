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
public class RocketMqShopStockDepletedEventPublisher implements ShopStockDepletedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqShopStockDepletedEventPublisher.class);

    private final DefaultMQProducer producer;
    private final String topic;

    public RocketMqShopStockDepletedEventPublisher(
            DefaultMQProducer producer,
            @Value("${shop.mq.topic:SHOP_EVENTS}") String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    public void publishDepleted(int productId, long atMs) {
        String body = "shopStockDepleted|productId=" + productId + "|atMs=" + atMs;
        try {
            producer.send(new Message(topic, "stock_depleted", body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("RocketMQ stock_depleted 发送失败 {}", body, e);
            throw new IllegalStateException("publish stock depleted failed", e);
        }
    }
}
