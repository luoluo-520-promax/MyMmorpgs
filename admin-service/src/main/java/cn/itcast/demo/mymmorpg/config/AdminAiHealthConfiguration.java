package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.ai.AiCostMeter;
import cn.itcast.demo.mymmorpg.health.AiServiceHealthIndicator;
import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AdminAiHealthConfiguration {

    @Bean
    @ConditionalOnBean(AiModelClient.class)
    public HealthIndicator aiServiceHealthIndicator(AiModelClient aiModelClient, AdminAiProperties properties) {
        AiMetrics metrics = aiModelClient.aiMetrics();
        AiCostMeter cost = aiModelClient.costMeter();
        return new AiServiceHealthIndicator(metrics, cost, properties.isEnabled(), 0.5, 50.0);
    }

    @Bean
    @ConditionalOnBean(AiModelClient.class)
    public AiMetrics adminAiMetrics(AiModelClient aiModelClient) {
        return aiModelClient.aiMetrics();
    }
}
