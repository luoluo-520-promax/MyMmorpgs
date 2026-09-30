/**
 * 文件维护说明
 * 1) 文件路径：quest-service/src/main/java/cn/itcast/demo/mymmorpg/service/QuestServiceApplication.java
 * 2) 所属模块：quest-service / service
 * 3) 主要职责：quest-service 的 Spring Boot 启动入口，负责扫描全量共享组件并加载任务服务。
 * 4) 变更建议：如需调整启动包扫描范围或默认配置，请同步检查内部接口、端口 Bean 与测试上下文。
 * 5) 风险提示：该入口由 spring.application.name 约束，改动后可能影响多服务同仓启动方式。
 */
package cn.itcast.demo.mymmorpg.service; // quest-service 启动入口：通过 spring.application.name 识别当前进程

import org.springframework.boot.SpringApplication; // Spring Boot 启动器
import org.springframework.boot.autoconfigure.SpringBootApplication; // 自动配置与组件扫描入口
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅 quest-service 实例激活该启动类

@ConditionalOnProperty(name = "spring.application.name", havingValue = "quest-service") // 只在 quest-service 进程中启用
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描共享包下的配置、服务、控制器与端口实现
public class QuestServiceApplication { // quest-service 的应用程序入口类

    public static void main(String[] args) { // Java 主启动方法
        SpringApplication.run(QuestServiceApplication.class, args); // 启动 Spring 容器并初始化 quest-service
    }
}
