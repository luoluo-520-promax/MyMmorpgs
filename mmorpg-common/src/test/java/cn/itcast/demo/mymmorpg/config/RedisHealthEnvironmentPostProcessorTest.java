/**
 * 文件说明：Redis 健康检查环境后处理器单元测试。
 * 职责：验证 RedisHealthEnvironmentPostProcessor 对 spring.autoconfigure.exclude 的合并与幂等行为。
 */
package cn.itcast.demo.mymmorpg.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisHealthEnvironmentPostProcessor 单元测试。
 */
public class RedisHealthEnvironmentPostProcessorTest {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthEnvironmentPostProcessorTest.class);

    private static final String EXCLUDE_KEY = "spring.autoconfigure.exclude";
    private static final String REDIS_HEALTH = "RedisHealthContributorAutoConfiguration";
    private static final String REDIS_REACTIVE_HEALTH = "RedisReactiveHealthContributorAutoConfiguration";

    private RedisHealthEnvironmentPostProcessor processor;
    private StandardEnvironment environment;
    private SpringApplication application;

    @BeforeMethod
    public void setUp() {
        processor = new RedisHealthEnvironmentPostProcessor();
        environment = new StandardEnvironment();
        application = new SpringApplication(Object.class);
        log.info("[测试前置] RedisHealthEnvironmentPostProcessor 与 StandardEnvironment 已初始化");
    }

    @Test
    public void postProcessEnvironment_blankExclude_addsRedisHealthAutoConfigs() {
        String existingExclude = environment.getProperty(EXCLUDE_KEY, "");
        log.info("[测试开始] 场景=空白 exclude 追加 Redis 健康自动配置 | existingExclude={} | excludeKey={}",
                existingExclude, EXCLUDE_KEY);

        processor.postProcessEnvironment(environment, application);

        String merged = environment.getProperty(EXCLUDE_KEY);
        log.info("[测试断言] 场景=空白 exclude 追加 Redis 健康自动配置 | merged={} | 期望包含={} 与 {}",
                merged, REDIS_HEALTH, REDIS_REACTIVE_HEALTH);
        assertThat(merged).contains(REDIS_HEALTH);
        assertThat(merged).contains(REDIS_REACTIVE_HEALTH);
    }

    @Test
    public void postProcessEnvironment_existingExclude_appendsRedisHealthAutoConfigs() {
        String existingExclude = "com.example.SomeAutoConfiguration";
        environment.getPropertySources().addLast(
                new MapPropertySource("testExisting", Map.of(EXCLUDE_KEY, existingExclude)));
        log.info("[测试开始] 场景=已有 exclude 追加 Redis 健康自动配置 | existingExclude={} | excludeKey={}",
                existingExclude, EXCLUDE_KEY);

        processor.postProcessEnvironment(environment, application);

        String merged = environment.getProperty(EXCLUDE_KEY);
        log.info("[测试断言] 场景=已有 exclude 追加 Redis 健康自动配置 | merged={} | 期望保留={} 且包含={}",
                merged, existingExclude, REDIS_REACTIVE_HEALTH);
        assertThat(merged).startsWith(existingExclude);
        assertThat(merged).contains(REDIS_HEALTH);
        assertThat(merged).contains(REDIS_REACTIVE_HEALTH);
    }

    @Test
    public void postProcessEnvironment_alreadyExcluded_isIdempotent() {
        String existingExclude = "org.springframework.boot.actuate.autoconfigure.data.redis."
                + REDIS_REACTIVE_HEALTH;
        environment.getPropertySources().addLast(
                new MapPropertySource("testAlreadyExcluded", Map.of(EXCLUDE_KEY, existingExclude)));
        log.info("[测试开始] 场景=已排除响应式 Redis 健康配置幂等 | existingExclude={} | excludeKey={}",
                existingExclude, EXCLUDE_KEY);

        processor.postProcessEnvironment(environment, application);

        String after = environment.getProperty(EXCLUDE_KEY);
        log.info("[测试断言] 场景=已排除响应式 Redis 健康配置幂等 | after={} | 期望={}", after, existingExclude);
        assertThat(after).isEqualTo(existingExclude);
    }
}
