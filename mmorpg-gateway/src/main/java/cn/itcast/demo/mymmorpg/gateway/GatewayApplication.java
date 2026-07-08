/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/GatewayApplication.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：微服务启动入口 GatewayApplication。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway; // 声明当前类所属的 Java 包，与目录结构 cn/itcast/demo/mymmorpg/gateway 对应

import org.springframework.boot.SpringApplication; // 提供 run 方法，负责创建 Spring 应用上下文并启动嵌入式 Netty 容器
import org.springframework.boot.autoconfigure.SpringBootApplication; // 组合注解：启用自动配置、组件扫描（默认扫描本包及子包）
import org.springframework.boot.context.properties.ConfigurationPropertiesScan; // 自动扫描并注册带 @ConfigurationProperties 的配置类（如 GatewayAuthProperties）


@SpringBootApplication // 标记为 Spring Boot 主应用，Gateway 模块所有 @Component/@Configuration 均在此包下被扫描
@ConfigurationPropertiesScan // 无需在每个 Properties 类上单独 @EnableConfigurationProperties，启动时统一扫描绑定 yml 配置
public class GatewayApplication { // MMORPG 网关微服务的 JVM 进程入口类

    /**
     * Java 标准 main 方法，IDE 或 java -jar 执行时从此处启动整个 gateway 进程。
     *
     * @param args 命令行参数，可传入 --spring.profiles.active=dev 等 Spring Boot 配置覆盖项
     */
    public static void main(String[] args) {
        // 以 GatewayApplication 为配置源启动 Spring 应用，内部会加载路由、全局过滤器、Redis 等 Bean
        SpringApplication.run(GatewayApplication.class, args);
    }
}
