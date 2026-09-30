package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;

@ConditionalOnProperty(name = "spring.application.name", havingValue = "gacha-service")
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg")
@EnableFeignClients(basePackages = "cn.itcast.demo.mymmorpg.client")
public class GachaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GachaServiceApplication.class, args);
    }
}
