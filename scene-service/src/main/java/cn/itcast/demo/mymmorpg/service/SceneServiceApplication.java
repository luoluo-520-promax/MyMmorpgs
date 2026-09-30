package cn.itcast.demo.mymmorpg.service; // scene-service 独立进程入口类所在包

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按 spring.application.name 决定是否激活本启动类，避免被 player-service 误扫
import org.springframework.boot.SpringApplication; // Spring Boot 引导器，负责创建 ApplicationContext 并启动内嵌容器
import org.springframework.boot.autoconfigure.SpringBootApplication; // 组合 @Configuration、@EnableAutoConfiguration、@ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling;

@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service") // 仅当 application.yml 中 name=scene-service 时注册本类为配置源
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描 cn.itcast.demo.mymmorpg 下全部 @Component/@Service/@Configuration
@EnableScheduling
public class SceneServiceApplication { // scene-service 微服务 JVM 主类

    public static void main(String[] args) { // JVM 入口：java -jar scene-service.jar 时执行
        SpringApplication.run(SceneServiceApplication.class, args); // 以本类为配置源启动 Spring 容器，加载 SceneActorService 等 Bean
    }
}
