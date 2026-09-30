package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@ConditionalOnProperty(name = "spring.application.name", havingValue = "social-service")
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg")
public class SocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialServiceApplication.class, args);
    }
}
