/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/test/java/cn/itcast/demo/mymmorpg/gateway/integration/GatewayApplicationIT.java
 * 2) 所属模块：mmorpg-gateway / test/java/cn/itcast/demo/mymmorpg/gateway/integration
 * 3) 主要职责：集成测试 Gateway 应用上下文启动与 /actuator/health 端点。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway.integration;

import cn.itcast.demo.mymmorpg.gateway.GatewayApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient; // 注入 WebTestClient 发 HTTP 请求
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(
        classes = GatewayApplication.class, // 启动完整 Gateway 应用
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, // 随机端口避免与本地 8080 冲突
        properties = {
                "spring.cloud.nacos.discovery.enabled=false", // IT 不依赖 Nacos 注册中心
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false", // 不用服务发现动态路由
                "game.gateway.rate-limit.enabled=false", // 集成测试不测 Redis 限流
                "game.gateway.auth.enabled=false", // 集成测试不测 Token 认证
                // 排除 Redis 自动配置，改用 GatewayRedisStubConfiguration 的 Mock Bean
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@Import(GatewayRedisStubConfiguration.class) // 提供 @Primary Mock StringRedisTemplate
@AutoConfigureWebTestClient
public class GatewayApplicationIT extends AbstractTestNGSpringContextTests {

    /** Spring 注入的响应式 HTTP 测试客户端，绑定随机端口 */
    @Autowired
    private WebTestClient webTestClient;

    /** 验证 Spring 上下文与 WebTestClient Bean 正常创建 */
    @Test
    public void contextLoads() {
        assertThat(webTestClient).isNotNull();
    }

    /** 调用 Actuator 健康检查，期望 HTTP 200 且 JSON status 为 UP */
    @Test
    public void actuatorHealthIsOk() {
        webTestClient.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
