package cn.itcast.demo.mymmorpg.service; // update-service 启动入口，负责装配更新子系统

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅在 update-service 进程中启用该启动类
import org.springframework.boot.SpringApplication; // Spring Boot 启动器，用于拉起应用上下文
import org.springframework.boot.autoconfigure.SpringBootApplication; // 标记主配置类并启用组件扫描
import org.springframework.cloud.openfeign.EnableFeignClients; // 启用 Feign 客户端扫描，供内部调用其他服务

@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描整个项目包，保证更新服务可复用 common 里的配置与组件
@EnableFeignClients(basePackages = "cn.itcast.demo.mymmorpg") // 允许更新服务按需调用其他服务的内部接口
@ConditionalOnProperty(name = "spring.application.name", havingValue = "update-service") // 仅当应用名为 update-service 时才真正生效
public class UpdateServiceApplication { // update-service 的 Spring Boot 主启动类

    public static void main(String[] args) { // JVM 入口，用于启动更新服务进程
        SpringApplication.run(UpdateServiceApplication.class, args); // 创建并启动 Spring 容器
    }
}
