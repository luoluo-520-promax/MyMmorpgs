/**
 * 文件说明：GatewayObserveConfiguration 单元测试。
 * 职责：验证 observabilityWebSocketService Bean 正确包装 delegate。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * GatewayObserveConfiguration 单元测试。
 */
public class GatewayObserveConfigurationTest {

    private static final Logger log = LoggerFactory.getLogger(GatewayObserveConfigurationTest.class);

    private GatewayObserveConfiguration configuration;

    @BeforeMethod
    public void setUp() {
        configuration = new GatewayObserveConfiguration();
        log.info("[测试前置] GatewayObserveConfiguration 已加载");
    }

    @Test
    public void observabilityWebSocketService_wrapsDelegate() {
        boolean enabled = true;
        boolean wsEnabled = true;
        double sampleRate = 1.0d;
        log.info("[测试开始] 场景=Bean包装 | enabled={} | wsEnabled={} | sampleRate={}",
                enabled, wsEnabled, sampleRate);

        WebSocketService delegate = mock(WebSocketService.class);
        GatewayObserveProperties props = new GatewayObserveProperties();
        props.setEnabled(enabled);
        props.setWsEnabled(wsEnabled);
        props.setSampleRate(sampleRate);

        WebSocketService bean = configuration.observabilityWebSocketService(delegate, props);

        log.info("[测试断言] 场景=Bean包装 | beanType={} | 期望={}",
                bean.getClass().getSimpleName(), ObservabilityWebSocketService.class.getSimpleName());
        assertThat(bean).isInstanceOf(ObservabilityWebSocketService.class);
    }
}
