package cn.itcast.demo.mymmorpg.service; // hall-service 启动入口，负责加载大厅服所有 Spring 组件

import org.springframework.boot.SpringApplication; // 启动 Spring Boot 应用上下文
import org.springframework.boot.autoconfigure.SpringBootApplication; // 声明这是 Spring Boot 主配置类
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅在 hall-service 进程中启用启动类

/**
 * 文件维护说明
 * 1) 文件路径：hall-service/src/main/java/cn/itcast/demo/mymmorpg/service/HallServiceApplication.java
 * 2) 所属模块：hall-service / service
 * 3) 主要职责：作为大厅服 Spring Boot 启动入口，扫描大厅相关组件并装配服务能力。
 * 4) 变更建议：如调整包结构或拆分模块，请同步确认 scanBasePackages 是否仍能覆盖大厅依赖。
 * 5) 风险提示：启动类是全模块入口之一，修改扫描范围可能导致内部控制器或端口 Bean 丢失。
 */
@ConditionalOnProperty(name = "spring.application.name", havingValue = "hall-service") // 仅大厅服实例启用该启动类
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描整个游戏根包，装配大厅所需 Bean
public class HallServiceApplication { // 大厅服应用启动器

    public static void main(String[] args) { // 标准 Java 进程入口
        SpringApplication.run(HallServiceApplication.class, args); // 启动 Spring 容器并加载大厅服上下文
    }
}
