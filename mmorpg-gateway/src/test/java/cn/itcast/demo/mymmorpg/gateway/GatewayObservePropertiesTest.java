/**
 * 文件说明：GatewayObserveProperties 单元测试。
 * 职责：验证观测开关、采样率、日志级别默认值与 setter 绑定。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayObserveProperties 单元测试。
 */
public class GatewayObservePropertiesTest {

    private static final Logger log = LoggerFactory.getLogger(GatewayObservePropertiesTest.class);

    @BeforeMethod
    public void setUp() {
        log.info("[测试前置] GatewayObserveProperties 测试环境就绪");
    }

    @Test
    public void defaults() {
        log.info("[测试开始] 场景=默认配置 | enabled期望=true | httpEnabled期望=true | wsEnabled期望=true | sampleRate期望=1.0 | logLevel期望=INFO");

        GatewayObserveProperties p = new GatewayObserveProperties();

        log.info("[测试断言] 场景=默认配置 | enabled={} | httpEnabled={} | wsEnabled={} | sampleRate={} | logLevel={}",
                p.isEnabled(), p.isHttpEnabled(), p.isWsEnabled(), p.getSampleRate(), p.getLogLevel());
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.isHttpEnabled()).isTrue();
        assertThat(p.isWsEnabled()).isTrue();
        assertThat(p.getSampleRate()).isEqualTo(1.0d);
        assertThat(p.getLogLevel()).isEqualTo("INFO");
    }

    @Test
    public void settersRoundTrip() {
        boolean enabled = false;
        boolean httpEnabled = false;
        boolean wsEnabled = false;
        double sampleRate = 0.25d;
        String logLevel = "DEBUG";
        log.info("[测试开始] 场景=setter绑定 | enabled={} | httpEnabled={} | wsEnabled={} | sampleRate={} | logLevel={}",
                enabled, httpEnabled, wsEnabled, sampleRate, logLevel);

        GatewayObserveProperties p = new GatewayObserveProperties();
        p.setEnabled(enabled);
        p.setHttpEnabled(httpEnabled);
        p.setWsEnabled(wsEnabled);
        p.setSampleRate(sampleRate);
        p.setLogLevel(logLevel);

        log.info("[测试断言] 场景=setter绑定 | enabled={} | httpEnabled={} | wsEnabled={} | sampleRate={} | logLevel={}",
                p.isEnabled(), p.isHttpEnabled(), p.isWsEnabled(), p.getSampleRate(), p.getLogLevel());
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.isHttpEnabled()).isFalse();
        assertThat(p.isWsEnabled()).isFalse();
        assertThat(p.getSampleRate()).isEqualTo(0.25d);
        assertThat(p.getLogLevel()).isEqualTo("DEBUG");
    }
}
