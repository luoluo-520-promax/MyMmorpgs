package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;

@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg")
@EnableFeignClients(basePackages = "cn.itcast.demo.mymmorpg.client")
@EnableScheduling
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
