/**
 * 文件说明
 * 模块：mmorpg-common / 配置
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/config/RedisPingHealthAutoConfiguration.java
 * 类型：类
 * 职责：定义 RedisPingHealthAutoConfiguration，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.config;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator; // management.health.redis.enabled
import org.springframework.boot.actuate.health.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean; // ?Bean 存在条件注册
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean; // 避免重复注册同名 Bean
import org.springframework.context.annotation.Bean; // 声明 Bean
import org.springframework.context.annotation.Configuration; // 配置
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory; // 响应式 Redis 连接工厂
import org.springframework.data.redis.connection.RedisConnection; // 阻塞 Redis 连接
import org.springframework.data.redis.connection.RedisConnectionFactory; // 阻塞 Redis 连接工厂
import reactor.core.publisher.Mono; // 响应式单

/**
 * Windows ?Redis / embedded-redis ?INFO 输出含反斜杠路径
 * Spring Data Redis ?Properties 解析时会失败
 * 改用 PING 做健康检查，避免依赖 INFO?
 */
@Configuration(proxyBeanMethods = false) // 轻量配置
@ConditionalOnEnabledHealthIndicator("redis") // 仅当 redis 健康指标启用时加
public class RedisPingHealthAutoConfiguration { // 自定义 Redis 健康检

    @Bean(name = "redisHealthContributor") // ?Spring Boot 默认 Bean 名一致，替换默认实现
    @ConditionalOnBean(ReactiveRedisConnectionFactory.class) // 存在响应式工厂时走此分支
    @ConditionalOnMissingBean(name = "redisHealthContributor") // 尚未注册则创
    /**
     * reactiveRedishealthcontributor；参数：ReactiveRedisConnectionFactory connectionFactory
     */
    ReactiveHealthContributor reactiveRedisHealthContributor(ReactiveRedisConnectionFactory connectionFactory) {
        return new AbstractReactiveHealthIndicator("Redis ping health check") { // 响应式 PING 检
            @Override
            /**
             * dohealthcheck；参数：org.springframework.boot.actuate.health.Health.Builder build
             */
            protected Mono<Health> doHealthCheck(Health.Builder builder) {
                return connectionFactory.getReactiveConnection().ping() // 发送 PING
                        .map(pong -> builder.up().withDetail("ping", pong).build()) // 成功则 UP
                        .onErrorResume(ex -> Mono.just(builder.down().withException(ex).build())); // 异常则 DOWN
            }
        };
    }

    @Configuration(proxyBeanMethods = false) // 内嵌配置：阻塞式 Redis
    @ConditionalOnBean(RedisConnectionFactory.class) // 有阻塞工
    @ConditionalOnMissingBean(ReactiveRedisConnectionFactory.class) // 且无响应式工厂（避免双注册）
    static class ImperativeRedisPingHealthConfiguration { // 阻塞式 PING 健康检

        @Bean(name = "redisHealthContributor")
        @ConditionalOnMissingBean(name = "redisHealthContributor")
        /**
         * Redishealthcontributor；参数：RedisConnectionFactory connectionFactory
         */
        HealthContributor redisHealthContributor(RedisConnectionFactory connectionFactory) {
            return new AbstractHealthIndicator("Redis ping health check") { // 同步 PING
                @Override
                /**
                 * dohealthcheck；参数：org.springframework.boot.actuate.health.Health.Builder build
                 */
                protected void doHealthCheck(org.springframework.boot.actuate.health.Health.Builder builder) throws Exception {
                    try (RedisConnection connection = connectionFactory.getConnection()) { // 借连接 PING
                        builder.up().withDetail("ping", connection.ping()); // 记录 PONG 详情
                    } // try-with-resources 自动关闭连接
                }
            };
        }
    }
}
