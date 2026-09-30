package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConditionalOnProperty(name = "spring.application.name", havingValue = "guild-service")
@SpringBootApplication(
        scanBasePackages = "cn.itcast.demo.mymmorpg",
        exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@EnableFeignClients(basePackages = "cn.itcast.demo.mymmorpg.client")
@EnableScheduling
public class GuildServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuildServiceApplication.class, args);
    }
}
