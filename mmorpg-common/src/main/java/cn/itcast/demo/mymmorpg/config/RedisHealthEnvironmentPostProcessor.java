/**
 * 文件说明
 * 模块：mmorpg-common / 配置
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/config/RedisHealthEnvironmentPostProcessor.java
 * 类型：类
 * 职责：定义 RedisHealthEnvironmentPostProcessor，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.SpringApplication; // 启动应用引用（EnvironmentPostProcessor 签名要求
import org.springframework.boot.env.EnvironmentPostProcessor; // ?Environment 就绪后、上下文创建前执
import org.springframework.core.env.ConfigurableEnvironment; // 可修改的 Environment
import org.springframework.core.env.MapPropertySource; // 内存属性源
import org.springframework.http.converter.json.GsonBuilderUtils;

import java.util.LinkedHashMap; // 保持插入顺序 Map
import java.util.Map; // 属性键值对

/**
 * 禁用默认 Redis INFO 健康检查（Windows 路径会导致解析失败），改用 RedisPingHealthConfiguration 使用 PING。
 */
public class RedisHealthEnvironmentPostProcessor implements EnvironmentPostProcessor { // SPI：spring.factories 加载

    /** exclude_key（类型：String） */
    private static final String EXCLUDE_KEY = "spring.autoconfigure.exclude"; // Spring Boot 排除自动配置的标准键

    /**
     * 执行方法逻辑
     */
    private static final String REDIS_HEALTH_AUTO_CONFIG = String.join(",", // 待排除的两个 Redis 健康自动配置类全
            "org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) { // 启动时回
        String existing = environment.getProperty(EXCLUDE_KEY, ""); // 读取已有 exclude 列表（可能为空）
        if (existing.contains("RedisReactiveHealthContributorAutoConfiguration")) { // 已排除则幂等返回
            return;  // 执行语句
        }
        String merged = existing.isBlank() ? REDIS_HEALTH_AUTO_CONFIG : existing + "," + REDIS_HEALTH_AUTO_CONFIG; // 追加排除
        Map<String, Object> properties = new LinkedHashMap<>(); // 构造高优先级属性源
        properties.put(EXCLUDE_KEY, merged); // 写入合并后的 exclude
        environment.getPropertySources().addFirst(new MapPropertySource("redisPingHealthExclude", properties)); // 插到最前，覆盖默认
    }
}