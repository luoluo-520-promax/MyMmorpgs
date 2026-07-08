/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/config/RedisPingHealthAutoConfiguration.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway/config
 * 3) 主要职责：为响应式 Redis 注册 PING 健康检查，供 /actuator/health 使用。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway.config;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator; // management.health.redis.enabled 为 true 时才注册
import org.springframework.boot.actuate.health.AbstractReactiveHealthIndicator; // 响应式健康检查基类，实现 doHealthCheck 返回 Mono<Health>
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthContributor; // Actuator 聚合健康状态时使用的贡献者接口
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean; // 仅当容器中存在 ReactiveRedisConnectionFactory 时注册
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean; // 避免与 Spring Boot 自带 redisHealthContributor 重复注册
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory; // 响应式 Redis 连接工厂，Gateway 限流/认证依赖 Redis
import reactor.core.publisher.Mono;


@Configuration(proxyBeanMethods = false) // 轻量配置类，不生成 CGLIB 代理，@Bean 方法不会被互相调用
@ConditionalOnEnabledHealthIndicator("redis") // 对应 management.health.redis.enabled，默认可通过配置关闭 redis 健康项
public class RedisPingHealthAutoConfiguration {

    /**
     * 注册名为 redisHealthContributor 的响应式 Redis 健康检查：
     * 对 Redis 执行 PING，成功则 UP 并附带 pong 详情，异常则 DOWN 并记录 exception。
     *
     * @param connectionFactory Spring Data Redis 响应式连接工厂
     * @return 供 Actuator /actuator/health 聚合的 ReactiveHealthContributor
     */
    @Bean(name = "redisHealthContributor")
    @ConditionalOnBean(ReactiveRedisConnectionFactory.class) // 未配置 Redis 时不创建此 Bean
    @ConditionalOnMissingBean(name = "redisHealthContributor") // 已有同名 Bean 时跳过，便于测试或自定义覆盖
    ReactiveHealthContributor reactiveRedisHealthContributor(ReactiveRedisConnectionFactory connectionFactory) {
        return new AbstractReactiveHealthIndicator("Redis ping health check") {

            /**
             * 执行 Redis PING 命令验证连通性，供 K8s 就绪探针或运维监控使用。
             */
            @Override
            protected Mono<Health> doHealthCheck(Health.Builder builder) {
                return connectionFactory.getReactiveConnection().ping()
                        // PING 返回 PONG 时标记 UP，detail 中记录 ping 响应内容
                        .map(pong -> builder.up().withDetail("ping", pong).build())
                        // 连接超时、认证失败等异常时标记 DOWN 并附带异常栈
                        .onErrorResume(ex -> Mono.just(builder.down().withException(ex).build()));
            }
        };
    }
}
