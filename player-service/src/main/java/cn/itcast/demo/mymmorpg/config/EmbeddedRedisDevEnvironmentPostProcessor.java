/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/EmbeddedRedisDevEnvironmentPostProcessor.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：在 Spring 上下文创建前读取 application.yml 并启动 embedded-redis，早于 RedisAutoConfiguration。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import org.springframework.boot.SpringApplication; // EnvironmentPostProcessor 回调参数，本实现未使用
import org.springframework.boot.env.EnvironmentPostProcessor; // SPI：在 ApplicationContext 刷新前修改 Environment
import org.springframework.core.env.ConfigurableEnvironment; // 读取 game.dev.* 与 spring.data.redis.*
/**
 * 在 Spring 上下文创建前启动嵌入式 Redis（读取 application.yml 配置）。
 * 注册于 META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports。
 */

public class EmbeddedRedisDevEnvironmentPostProcessor implements EnvironmentPostProcessor { // EmbeddedRedisDevEnvironmentPostProcessor 类型定义
    /**
     * 启动极早阶段：RedisConnectionFactory Bean 尚未创建，需在此保证 Redis 端口已监听。
     */

    @Override // 实现接口/父类方法
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) { // EmbeddedRedisDevEnvironmentPostProcessor.postProcessEnvironment：ConfigurableEnvironment environment, SpringApplication appli
        boolean enabled = environment.getProperty("game.dev.embedded-redis.enabled", Boolean.class, true); // 默认开发启用
        String host = environment.getProperty("spring.data.redis.host", "localhost"); // 与 StringRedisTemplate 一致
        int port = environment.getProperty("spring.data.redis.port", Integer.class, 6379); // EmbeddedRedisDevEnvironmentPostProcessor 逻辑
        EmbeddedRedisDevSupport.startIfNeeded(enabled, host, port); // 端口空闲则 start，已有 Redis 则 skip
    } // postProcessEnvironment 方法体结束
} // EmbeddedRedisDevEnvironmentPostProcessor 类体结束
