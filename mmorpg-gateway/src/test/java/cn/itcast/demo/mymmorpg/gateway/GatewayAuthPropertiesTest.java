/**
 * 文件说明：GatewayAuthProperties 单元测试。
 * 职责：验证认证配置默认值与 setter 绑定行为。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayAuthProperties 单元测试。
 */
public class GatewayAuthPropertiesTest {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthPropertiesTest.class);

    @BeforeMethod
    public void setUp() {
        log.info("[测试前置] GatewayAuthProperties 测试环境就绪");
    }

    @Test
    public void defaults() {
        log.info("[测试开始] 场景=默认配置 | enabled期望=true | whitelist期望含/ws");

        GatewayAuthProperties p = new GatewayAuthProperties();
        boolean enabled = p.isEnabled();
        List<String> whitelist = p.getWhitelist();

        log.info("[测试断言] 场景=默认配置 | enabled={} | whitelist={} | 期望enabled=true且whitelist非空",
                enabled, whitelist);
        assertThat(enabled).isTrue();
        assertThat(whitelist).isNotEmpty();
        assertThat(whitelist).contains("/ws");
    }

    @Test
    public void setters() {
        boolean enabled = false;
        List<String> whitelist = List.of("/a/**");
        log.info("[测试开始] 场景=setter绑定 | enabled={} | whitelist={}", enabled, whitelist);

        GatewayAuthProperties p = new GatewayAuthProperties();
        p.setEnabled(enabled);
        p.setWhitelist(whitelist);

        log.info("[测试断言] 场景=setter绑定 | enabled={} | whitelist={} | 期望与输入一致",
                p.isEnabled(), p.getWhitelist());
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.getWhitelist()).containsExactly("/a/**");
    }
}
