/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/MyMmorpgApplication.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg
 * 3) 主要职责：player-service 启动入口，扫描组件、Feign 客户端、定时任务，并在 main 中二次 bootstrap Redis。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg; // player-service 根包，Spring Boot 启动类所在命名空间

import cn.itcast.demo.mymmorpg.config.EmbeddedRedisDevSupport; // player-service 配置 Bean
import org.springframework.boot.SpringApplication; // Spring Boot 自动配置与 CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication; // Spring Boot 自动配置与 CommandLineRunner
import org.springframework.boot.context.properties.ConfigurationPropertiesScan; // Spring Boot 自动配置与 CommandLineRunner
import org.springframework.cloud.openfeign.EnableFeignClients; // Feign 远程调用 EnableFeignClients
import org.springframework.scheduling.annotation.EnableScheduling; // EnableScheduling，MyMmorpgApplication.java 编译依赖

@SpringBootApplication(scanBasePackages = "cn.itcast.demo.mymmorpg") // 扫描 config/event/service/net/handler 全包
@EnableScheduling // WsSessionIdleReaper 等定时任务

@ConfigurationPropertiesScan // 扫描 game.function.* 等 @ConfigurationProperties
@EnableFeignClients(basePackages = "cn.itcast.demo.mymmorpg.client") // 微服务模式下注册 Battle/Activity Feign 代理

public class MyMmorpgApplication { // MyMmorpgApplication 类型定义
    /**
     * JVM 入口：先 bootstrap Redis（环境变量优先），再启动 SpringApplication。
     */

    public static void main(String[] args) { // JVM 入口，bootstrap Redis 后启动 SpringApplication
        bootstrapEmbeddedRedis(); // IDE 直接 main 时确保 Redis 可用，与 PostProcessor 双保险
        SpringApplication.run(MyMmorpgApplication.class, args); // 启动 player-service 容器
    } // main 方法体结束
    /**
     * 从环境变量或 System Property 读取 Redis 开关与地址，委托 EmbeddedRedisDevSupport 启动。
     * 优先级：EMBEDDED_REDIS_ENABLED / REDIS_HOST / REDIS_PORT > 默认值。
     */

    private static void bootstrapEmbeddedRedis() { // MyMmorpgApplication.bootstrapEmbeddedRedis：无参
        boolean enabled = !"false".equalsIgnoreCase(envOrDefault("EMBEDDED_REDIS_ENABLED", "true")); // 显式 false 关闭内嵌 Redis
        String host = envOrDefault("REDIS_HOST", "localhost"); // Redis 主机地址
        int port = Integer.parseInt(envOrDefault("REDIS_PORT", "6379")); // Redis 端口
        EmbeddedRedisDevSupport.startIfNeeded(enabled, host, port); // 本地开发启动内嵌 Redis 实例
    } // bootstrapEmbeddedRedis 方法体结束
    /** 先读 OS 环境变量，缺失时读 -D 系统属性，最后回落 defaultValue */

    private static String envOrDefault(String key, String defaultValue) { // MyMmorpgApplication.envOrDefault：String key, String defaultValue
        String fromEnv = System.getenv(key); // 优先取 OS 环境变量
        if (fromEnv != null && !fromEnv.isBlank()) { // MyMmorpgApplication 方法
            return fromEnv; // 环境变量存在且非空
        } // if 方法体结束
        return System.getProperty(key, defaultValue); // 回落 JVM -D 参数或默认值
    } // envOrDefault 方法体结束
} // MyMmorpgApplication 类体结束
