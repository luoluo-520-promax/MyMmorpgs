package cn.itcast.demo.mymmorpg.service; // skill-service 独立进程入口类所在包

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 按 spring.application.name 决定是否激活本启动类
import org.springframework.boot.SpringApplication; // Spring Boot 引导器
import org.springframework.boot.autoconfigure.SpringBootApplication; // 启用自动配置与组件扫描

@ConditionalOnProperty(name = "spring.application.name", havingValue = "skill-service") // 仅 name=skill-service 时注册，避免 player-service 误启动第二份 SkillService
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描 SkillService、SkillPolicyConfiguration、GroovySkillPolicy 等
public class SkillServiceApplication { // skill-service 微服务 JVM 主类

    public static void main(String[] args) { // JVM 入口
        SpringApplication.run(SkillServiceApplication.class, args); // 启动 Spring 容器，加载 SkillService 与 Redis/JPA 自动配置
    }
}
