/**
 * 文件说明：战斗服务完整上下文集成测试。
 * 职责：启动 BattleServiceApplication 完整 Spring 上下文 + 真实 Servlet 容器，
 *       校验应用能正常启动且 Actuator 健康检查为 UP。
 */
package cn.itcast.demo.mymmorpg.service.integration;

import cn.itcast.demo.mymmorpg.service.integration.support.RedisTestContainerHolder; // 测试 Redis 工具
import cn.itcast.demo.mymmorpg.service.BattleServiceApplication; // 战斗服务主类
import org.springframework.beans.factory.annotation.Autowired; // 自动注入
import org.springframework.boot.test.context.SpringBootTest; // 完整 Spring Boot 测试
import org.springframework.boot.test.web.client.TestRestTemplate; // HTTP 测试客户端
import org.springframework.http.HttpStatus; // HTTP 状态码
import org.springframework.http.ResponseEntity; // HTTP 响应
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests; // TestNG Spring 基类
import org.testng.annotations.Test; // 测试注解

import static org.assertj.core.api.Assertions.assertThat; // 断言工具

/**
 * 战斗服务完整上下文 + 真实 Servlet 容器端口：校验启动与 Actuator。
 */
@SpringBootTest( // 启动 BattleServiceApplication
        classes = BattleServiceApplication.class, // 显式指定主类，避免 IDE 找不到 @SpringBootConfiguration
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, // 随机端口启动 Web 容器
        properties = { // 测试专用配置
                "spring.application.name=battle-service", // 满足 @ConditionalOnProperty
                "spring.cloud.nacos.config.enabled=false", // 关闭 Nacos 配置
                "spring.cloud.nacos.config.import-check.enabled=false", // 关闭 Nacos 导入检查
                "spring.datasource.url=jdbc:h2:mem:battle_it;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", // 战斗 IT 专用 H2 内存库
                "spring.datasource.driver-class-name=org.h2.Driver", // H2 驱动
                "spring.datasource.username=sa", // 用户名
                "spring.datasource.password=", // 空密码
                "spring.jpa.hibernate.ddl-auto=create-drop", // 测试结束删表
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", // H2 方言
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", // Hibernate 方言
                "rocketmq.enabled=false", // 关闭 RocketMQ
                "game.data-warmup.enabled=false", // 关闭数据预热
                "game.player-preload.enabled=false", // 关闭玩家预加载
                "game.idempotency.enabled=false", // 关闭幂等
                "spring.cloud.nacos.discovery.enabled=false", // 关闭 Nacos 服务发现
                "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration"
        })
public class BattleServiceApplicationIT extends AbstractTestNGSpringContextTests { // 战斗服务集成测试

    /** 静态块：类加载时启动测试 Redis */
    static {
        RedisTestContainerHolder.ensureStarted(); // 类加载时启动 Redis
    }

    /** HTTP 测试客户端 */
    @Autowired
    private TestRestTemplate testRestTemplate; // HTTP 测试客户端

    /**
     * 测试：Spring 上下文应能正常加载。
     */
    @Test
    public void contextLoads() { // 上下文冒烟测试
        assertThat(testRestTemplate).isNotNull(); // 验证 TestRestTemplate 已注入
    }

    /**
     * 测试：Actuator 健康检查应返回 UP。
     */
    @Test
    public void actuatorHealthIsUp() { // Actuator 健康检查
        ResponseEntity<String> res = testRestTemplate.getForEntity("/actuator/health", String.class); // 请求健康端点
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK); // HTTP 200
        assertThat(res.getBody()).isNotNull(); // 响应体非空
        assertThat(res.getBody()).containsIgnoringCase("UP"); // 状态为 UP
    }
}
