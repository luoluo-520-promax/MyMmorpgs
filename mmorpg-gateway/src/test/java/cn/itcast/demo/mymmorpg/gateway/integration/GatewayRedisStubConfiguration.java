/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/test/java/cn/itcast/demo/mymmorpg/gateway/integration/GatewayRedisStubConfiguration.java
 * 2) 所属模块：mmorpg-gateway / test/java/cn/itcast/demo/mymmorpg/gateway/integration
 * 3) 主要职责：集成测试用 Mock Redis Bean，避免 IT 依赖外部 Redis 服务。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway.integration;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration; // 仅测试上下文生效，不会打入生产 jar
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary; // 覆盖 RedisAutoConfiguration 可能注册的 Bean
import org.springframework.data.redis.core.StringRedisTemplate;


@TestConfiguration
public class GatewayRedisStubConfiguration {

    /**
     * 提供 Mockito mock 的 StringRedisTemplate，
     * AuthGlobalFilter / RateLimitGlobalFilter 注入时不会连接真实 Redis。
     */
    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return Mockito.mock(StringRedisTemplate.class);
    }
}
