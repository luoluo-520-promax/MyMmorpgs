/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/test/java/cn/itcast/demo/mymmorpg/integration/PlayerServiceApplicationIT.java
 * 2) 所属模块：player-service / test / integration
 * 3) 主要职责：玩家服务全上下文集成测试（H2、Redis、Feign 占位 URL、Actuator）? * 4) 变更建议：与 activity/battle ?ApplicationIT 保持 properties 结构一致? * 5) 风险提示：Feign URL 为占位端口，IT 不发起真实远程调用? */
package cn.itcast.demo.mymmorpg.integration;
import cn.itcast.demo.mymmorpg.MyMmorpgApplication; // 玩家服务主类
import cn.itcast.demo.mymmorpg.test.support.RedisTestContainerHolder;
import org.springframework.beans.factory.annotation.Autowired; // 自动注入依赖
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus; // HTTP 状态码枚举
import org.springframework.http.ResponseEntity; // HTTP 响应封装
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test; // 单元测试方法

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 玩家服务完整上下文：内存 H2、Redis（Docker 上为 Testcontainers，否?embedded-redis）、关?Nacos/SSL，Feign 指向占位 battle URL（无真实调用）? */
@SpringBootTest( // 启动 MyMmorpgApplication 完整上下?        classes = MyMmorpgApplication.class, // 明确主类
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, // 随机 HTTP 端口
        properties = { // IT 专用配置
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.config.import-check.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:player_it;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", // 内存 H2
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "server.ssl.enabled=false", // 不测 HTTPS
                "server.port=0", // ?RANDOM_PORT 配合
                "game.netty.port=0", // 不测 Netty 游戏端口
                "game.netty.enabled=false",
                "game.kcp.enabled=false",
                "rocketmq.enabled=false",
                "spring.cloud.openfeign.client.config.battle-service.url=http://127.0.0.1:8991", // Feign 占位，避免连真实 battle
                "spring.cloud.openfeign.client.config.activity-service.url=http://127.0.0.1:8992", // Feign 占位
                "game.data-warmup.enabled=false",
                "game.player-preload.enabled=false",
                "game.idempotency.enabled=false",
                "spring.profiles.active=test",
                "game.http-security.enabled=false",
                "game.internal-api.secret=test-internal-secret"
        })
public class PlayerServiceApplicationIT extends AbstractTestNGSpringContextTests { // 玩家服务集成测试

    static {
        RedisTestContainerHolder.ensureStarted(); // 必须?Spring 刷新前设?redis 属?
    }

    @Autowired
    private TestRestTemplate testRestTemplate; // HTTP 测试客户?
    @Test
    public void contextLoads() { // 冒烟：上下文可启?        assertThat(testRestTemplate).isNotNull();
    }

    @Test
    public void actuatorHealthIsUp() { // Actuator 健康 UP
        ResponseEntity<String> res = testRestTemplate.getForEntity("/actuator/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).containsIgnoringCase("UP");
    }
}
