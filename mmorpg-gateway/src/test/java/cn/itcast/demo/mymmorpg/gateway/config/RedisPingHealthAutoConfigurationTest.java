/**
 * 文件说明：RedisPingHealthAutoConfiguration 单元测试。
 * 职责：验证 Redis PING 健康检查在成功与失败时的 UP/DOWN 状态。
 */
package cn.itcast.demo.mymmorpg.gateway.config;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RedisPingHealthAutoConfiguration 单元测试。
 */
public class RedisPingHealthAutoConfigurationTest {

    private static final Logger log = LoggerFactory.getLogger(RedisPingHealthAutoConfigurationTest.class);

    @Mock
    private ReactiveRedisConnectionFactory connectionFactory;

    @Mock
    private ReactiveRedisConnection connection;

    private AutoCloseable mocks;

    private RedisPingHealthAutoConfiguration configuration;

    @BeforeMethod
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        configuration = new RedisPingHealthAutoConfiguration();
        when(connectionFactory.getReactiveConnection()).thenReturn(connection);
        log.info("[测试前置] RedisPingHealthAutoConfiguration Mock 依赖已初始化");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void whenPingReturnsPong_healthIsUp() {
        String pong = "PONG";
        log.info("[测试开始] 场景=Redis PING成功 | pingResponse={}", pong);

        when(connection.ping()).thenReturn(Mono.just(pong));
        ReactiveHealthContributor contributor = configuration.reactiveRedisHealthContributor(connectionFactory);
        ReactiveHealthIndicator indicator = (ReactiveHealthIndicator) contributor;

        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    log.info("[测试断言] 场景=Redis PING成功 | status={} | pingDetail={} | 期望status=UP",
                            health.getStatus(), health.getDetails().get("ping"));
                    assertThat(health.getStatus()).isEqualTo(Health.up().build().getStatus());
                    assertThat(health.getDetails()).containsEntry("ping", pong);
                })
                .verifyComplete();
    }

    @Test
    public void whenPingFails_healthIsDown() {
        String errorMessage = "Connection refused";
        log.info("[测试开始] 场景=Redis PING失败 | errorMessage={}", errorMessage);

        when(connection.ping()).thenReturn(Mono.error(new RuntimeException(errorMessage)));
        ReactiveHealthContributor contributor = configuration.reactiveRedisHealthContributor(connectionFactory);
        ReactiveHealthIndicator indicator = (ReactiveHealthIndicator) contributor;

        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    log.info("[测试断言] 场景=Redis PING失败 | status={} | hasException={} | 期望status=DOWN",
                            health.getStatus(), health.getDetails().containsKey("error"));
                    assertThat(health.getStatus()).isEqualTo(Health.down().build().getStatus());
                })
                .verifyComplete();
    }
}
