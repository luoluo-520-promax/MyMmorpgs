/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/service/ActivityServiceApplication.java
 * 2) 所属模块：activity-service / service
 * 3) 主要职责：活动微服务 Spring Boot 启动入口，扫描 common 包并启用 Feign 客户端
 * 4) 系统位置：activity-service 模块根启动类
 * 5) 变更建议：新增 Feign 客户端包路径时同步更新 @EnableFeignClients
 */
package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.SpringApplication; // Spring Boot 应用启动器
import org.springframework.boot.autoconfigure.SpringBootApplication; // 自动配置与组件扫描
import org.springframework.cloud.openfeign.EnableFeignClients; // 启用 Feign 远程调用
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 活动微服务启动类。
 */
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描整个 mmorpg 公共包
@EnableFeignClients(basePackages = "cn.itcast.demo.mymmorpg") // 启用 Feign 客户端（如背包、玩家通知等）
@EnableScheduling
public class ActivityServiceApplication { // 活动服务入口

    /**
     * 应用主入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ActivityServiceApplication.class, args); // 启动 Spring Boot 应用
    }
}
