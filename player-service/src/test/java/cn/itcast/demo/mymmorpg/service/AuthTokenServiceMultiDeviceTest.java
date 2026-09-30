package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SessionLoginProperties;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多端登录策略配置解析（不依赖 Redis mock）。
 */
public class AuthTokenServiceMultiDeviceTest {

    @Test
    public void loginPolicy_parsesConfiguredModes() {
        assertThat(SessionLoginProperties.Policy.from("single_device"))
                .isEqualTo(SessionLoginProperties.Policy.SINGLE_DEVICE);
        assertThat(SessionLoginProperties.Policy.from("multi_device_allowlist"))
                .isEqualTo(SessionLoginProperties.Policy.MULTI_DEVICE_ALLOWLIST);
        assertThat(SessionLoginProperties.Policy.from("kick_oldest"))
                .isEqualTo(SessionLoginProperties.Policy.KICK_OLDEST);
    }

    @Test
    public void sessionDefaults_supportMultiDeviceAllowlist() {
        SessionLoginProperties props = new SessionLoginProperties();
        props.setLoginPolicy("multi_device_allowlist");
        props.setMaxSessions(3);
        props.setTokenTtlHours(2);
        assertThat(props.getAllowClientTypes()).contains("PC", "MOBILE", "CONSOLE");
        assertThat(props.getMaxSessions()).isEqualTo(3);
        assertThat(props.getTokenTtlHours()).isEqualTo(2L);
    }
}
