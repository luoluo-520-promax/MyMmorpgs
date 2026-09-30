package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConditionalOnProperty(name = "spring.application.name", havingValue = "bag-service")
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg")
@EnableScheduling
public class BagServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BagServiceApplication.class, args);
    }
}
