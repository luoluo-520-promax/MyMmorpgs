/**
 * 文件维护说明：`matchmaking-service` 的 Spring Boot 启动入口。
 * <p>
 * 路径：`matchmaking-service/src/main/java/cn/itcast/demo/mymmorpg/service/MatchmakingServiceApplication.java`
 * <br>模块：匹配服应用入口
 * <br>职责：启动匹配服 Spring 容器并扫描 `cn.itcast.demo.mymmorpg` 下的所有匹配相关组件、控制器与配置。
 * <br>变更建议：仅在修改应用启动方式、组件扫描范围或服务名判断策略时调整；若重命名模块，应同步检查条件装配与配置文件。
 * <br>风险提示：应用入口的服务名判断直接影响配置类、控制器和端口装配是否生效，修改前需确认与部署脚本一致。
 */
package cn.itcast.demo.mymmorpg.service; // 匹配服服务包，包含启动入口与核心业务服务

import org.springframework.boot.SpringApplication; // Spring Boot 启动器，用于拉起整个应用上下文
import org.springframework.boot.autoconfigure.SpringBootApplication; // 标识 Spring Boot 应用入口
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 仅在指定应用名下激活匹配服启动类
import org.springframework.scheduling.annotation.EnableScheduling;

@ConditionalOnProperty(name = "spring.application.name", havingValue = "matchmaking-service") // 只有匹配服进程使用该启动入口，避免其他模块误启动
@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描公共包与本模块包，装配匹配相关 Bean 与控制器
@EnableScheduling
public class MatchmakingServiceApplication { // 匹配服 Spring Boot 启动类，负责容器启动与组件扫描

    public static void main(String[] args) { // 应用主方法，供容器与脚本直接启动匹配服
        SpringApplication.run(MatchmakingServiceApplication.class, args); // 拉起 Spring Boot 应用上下文并完成自动装配
    }
}
