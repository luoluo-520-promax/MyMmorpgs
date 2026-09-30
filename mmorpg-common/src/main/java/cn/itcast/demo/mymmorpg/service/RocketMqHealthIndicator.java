package cn.itcast.demo.mymmorpg.service;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@ConditionalOnBean(DefaultMQProducer.class)
public class RocketMqHealthIndicator implements HealthIndicator {

    private final DefaultMQProducer producer;

    public RocketMqHealthIndicator(DefaultMQProducer producer) {
        this.producer = producer;
    }

    @Override
    public Health health() {
        try {
            String addr = producer.getNamesrvAddr();
            if (addr == null || addr.isBlank()) {
                return Health.down().withDetail("reason", "NameServer 未配置").build();
            }
            return Health.up().withDetail("nameServer", addr).withDetail("group", producer.getProducerGroup()).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
