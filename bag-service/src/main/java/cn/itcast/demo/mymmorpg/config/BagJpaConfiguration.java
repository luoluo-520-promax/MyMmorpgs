package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "spring.application.name", havingValue = "bag-service")
@EnableJpaRepositories(basePackages = "cn.itcast.demo.mymmorpg.repository")
@EntityScan(basePackages = "cn.itcast.demo.mymmorpg.entity")
public class BagJpaConfiguration {
}
