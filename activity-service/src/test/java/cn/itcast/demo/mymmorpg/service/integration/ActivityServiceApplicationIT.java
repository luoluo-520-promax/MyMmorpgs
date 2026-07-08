/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/test/java/cn/itcast/demo/mymmorpg/service/integration/ActivityServiceApplicationIT.java
 * 2) 所属模块：activity-service / test / integration
 * 3) 主要职责：Spring Boot 全上下文集成测试，校验启动与 Actuator 健康检查
 * 4) 变更建议：新增关键 Bean 或端口冲突时在 properties 中关闭 Nacos/MQ/预热等
 * 5) 风险提示：依赖 RedisTestContainerHolder；无 Docker 时回退 embedded-redis
 */
package cn.itcast.demo.mymmorpg.service.integration;

import cn.itcast.demo.mymmorpg.service.integration.support.RedisTestContainerHolder; // 测试 Redis 生命周期
import cn.itcast.demo.mymmorpg.service.ActivityServiceApplication; // 活动服务主类
import org.springframework.beans.factory.annotation.Autowired; // 注入 TestRestTemplate
import org.springframework.boot.test.context.SpringBootTest; // 全上下文启动
import org.springframework.boot.test.web.client.TestRestTemplate; // HTTP 客户端
import org.springframework.http.HttpStatus; // HTTP 状态码
import org.springframework.http.ResponseEntity; // HTTP 响应
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests; // TestNG + Spring 基类
import org.testng.annotations.Test; // TestNG 测试注解

import static org.assertj.core.api.Assertions.assertThat; // AssertJ 断言

/**
 * 活动服务完整上下文 + 真实 Servlet 容器端口：校验启动与 Actuator。
 */
@SpringBootTest(
        classes = ActivityServiceApplication.class, // 显式指定主类
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, // 随机端口启动嵌入式 Tomcat
        properties = {
                "spring.application.name=activity-service", // 满足 @ConditionalOnProperty
                "spring.cloud.nacos.discovery.enabled=false", // 关闭 Nacos 服务发现
                "spring.cloud.nacos.config.enabled=false", // 关闭 Nacos 配置
                "spring.cloud.nacos.config.import-check.enabled=false", // 跳过 Nacos import 检查
                "spring.datasource.url=jdbc:h2:mem:activity_it;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", // 内存 H2 模拟 MySQL
                "spring.datasource.driver-class-name=org.h2.Driver", // H2 驱动
                "spring.datasource.username=sa", // H2 默认用户名
                "spring.datasource.password=", // H2 空密码
                "spring.jpa.hibernate.ddl-auto=create-drop", // 测试结束删表
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", // H2 方言
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", // Hibernate 方言
                "rocketmq.enabled=false", // 关闭 RocketMQ
                "game.data-warmup.enabled=false", // 关闭数据预热
                "game.player-preload.enabled=false", // 关闭玩家预加载
                "game.idempotency.enabled=false", // 关闭幂等
                "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration,org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration" // 禁用默认 Redis INFO 健康检查（Windows/embedded-redis 兼容）
        })
public class ActivityServiceApplicationIT extends AbstractTestNGSpringContextTests { // 集成测试类

    static { // 类加载时执行一次
        RedisTestContainerHolder.ensureStarted(); // 启动 Testcontainers 或 embedded Redis，并设置 spring.data.redis.*
    }

    /** Spring Boot 提供的 REST 测试客户端。 */
    @Autowired
    private TestRestTemplate testRestTemplate; // 用于 HTTP 调用 Actuator

    /**
     * 冒烟测试：Spring 上下文能启动且能注入 Bean。
     */
    @Test
    public void contextLoads() { // 冒烟：上下文能起来且能注入 Bean
        assertThat(testRestTemplate).isNotNull(); // 断言 TestRestTemplate 已注入
    }

    /**
     * 健康检查端点应返回 HTTP 200 且 body 含 UP。
     */
    @Test
    public void actuatorHealthIsUp() { // 健康检查端点返回 UP
        ResponseEntity<String> res = testRestTemplate.getForEntity("/actuator/health", String.class); // GET 健康端点
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK); // 期望 HTTP 200
        assertThat(res.getBody()).isNotNull(); // body 非空
        assertThat(res.getBody()).containsIgnoringCase("UP"); // JSON 中含 UP 状态
    }
}
