package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.shop.ShopOrderPaidEventPublisher;
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
public class RocketMqShopOrderPaidEventPublisher implements ShopOrderPaidEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqShopOrderPaidEventPublisher.class);

    private final DefaultMQProducer producer;

    @Value("${shop.mq.topic:SHOP_EVENTS}")
    private String topic;

    public RocketMqShopOrderPaidEventPublisher(DefaultMQProducer producer) {
        this.producer = producer;
    }

    @Override
    public void publishPaid(long playerId, String orderId, int productId, long payAmount, String productType) {
        String body = "shopOrderPaid|playerId=" + playerId
                + "|orderId=" + orderId
                + "|productId=" + productId
                + "|amount=" + payAmount
                + "|productType=" + (productType == null ? "" : productType);
        try {
            producer.send(new Message(topic, "paid", body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("RocketMQ ShopOrderPaid 发送失败 {}", body, e);
        }
    }
}
