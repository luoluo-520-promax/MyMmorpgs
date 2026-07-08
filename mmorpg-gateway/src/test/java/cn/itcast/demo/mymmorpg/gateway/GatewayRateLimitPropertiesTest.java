/**
 * 文件说明：GatewayRateLimitProperties 单元测试。
 * 职责：验证限流配置默认 QPS 阈值与白名单。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayRateLimitProperties 单元测试。
 */
public class GatewayRateLimitPropertiesTest {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitPropertiesTest.class);

    @BeforeMethod
    public void setUp() {
        log.info("[测试前置] GatewayRateLimitProperties 测试环境就绪");
    }

    @Test
    public void defaults() {
        log.info("[测试开始] 场景=默认配置 | enabled期望=true | totalPerSecond期望=2000 | userPerSecond期望=30");

        GatewayRateLimitProperties p = new GatewayRateLimitProperties();
        boolean enabled = p.isEnabled();
        int totalPerSecond = p.getTotalPerSecond();
        int userPerSecond = p.getUserPerSecond();
        List<String> whitelist = p.getWhitelist();

        log.info("[测试断言] 场景=默认配置 | enabled={} | totalPerSecond={} | userPerSecond={} | whitelist={}",
                enabled, totalPerSecond, userPerSecond, whitelist);
        assertThat(enabled).isTrue();
        assertThat(totalPerSecond).isEqualTo(2000);
        assertThat(userPerSecond).isEqualTo(30);
        assertThat(whitelist).isNotEmpty();
        assertThat(whitelist).contains("/actuator/**");
    }

    @Test
    public void setters() {
        boolean enabled = false;
        int totalPerSecond = 10;
        int userPerSecond = 5;
        List<String> whitelist = List.of("/x");
        log.info("[测试开始] 场景=setter绑定 | enabled={} | totalPerSecond={} | userPerSecond={} | whitelist={}",
                enabled, totalPerSecond, userPerSecond, whitelist);

        GatewayRateLimitProperties p = new GatewayRateLimitProperties();
        p.setEnabled(enabled);
        p.setTotalPerSecond(totalPerSecond);
        p.setUserPerSecond(userPerSecond);
        p.setWhitelist(whitelist);

        log.info("[测试断言] 场景=setter绑定 | enabled={} | totalPerSecond={} | userPerSecond={} | whitelist={}",
                p.isEnabled(), p.getTotalPerSecond(), p.getUserPerSecond(), p.getWhitelist());
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getTotalPerSecond()).isEqualTo(10);
        assertThat(p.getUserPerSecond()).isEqualTo(5);
        assertThat(p.getWhitelist()).containsExactly("/x");
    }
}
