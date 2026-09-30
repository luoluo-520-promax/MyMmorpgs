package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "spring.application.name", havingValue = "shop-service")
@EnableJpaRepositories(basePackageClasses = {
        cn.itcast.demo.mymmorpg.repository.ShopOrderRecordRepository.class,
        cn.itcast.demo.mymmorpg.repository.MqOutboxEventRepository.class
})
@EntityScan(basePackageClasses = {
        cn.itcast.demo.mymmorpg.entity.ShopOrderRecord.class,
        cn.itcast.demo.mymmorpg.entity.MqOutboxEvent.class
})
public class ShopJpaConfiguration {
}
