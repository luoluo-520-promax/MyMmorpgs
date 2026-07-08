/**
 * 文件说明：MQ 消息类型注册表单元测试。
 * 职责：验证 MqMessageTypeRegistry 的 register / find 行为。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MqMessageTypeRegistryTest {

    private static final Logger log = LoggerFactory.getLogger(MqMessageTypeRegistryTest.class);

    private MqMessageTypeRegistry registry;

    @BeforeMethod
    public void setUp() {
        registry = new MqMessageTypeRegistry();
        log.info("[测试前置] MqMessageTypeRegistry 已加载");
    }

    static final class DemoMqMessage implements MqMessage {
        @Override
        public String receiver() {
            return "player-service";
        }
    }

    @Test
    public void registerAndFind_returnsRegisteredType() {
        log.info("[测试开始] 场景=注册消息类型 | messageType={}", DemoMqMessage.class.getName());

        registry.register(DemoMqMessage.class);
        Class<? extends MqMessage> found = registry.find(DemoMqMessage.class.getName());

        log.info("[测试断言] 场景=注册消息类型 | found={} | 期望={}", found, DemoMqMessage.class);
        assertThat(found).isEqualTo(DemoMqMessage.class);
    }

    @Test
    public void register_sameTypeTwice_isIdempotent() {
        log.info("[测试开始] 场景=重复注册同类型 | messageType={}", DemoMqMessage.class.getName());

        registry.register(DemoMqMessage.class);
        registry.register(DemoMqMessage.class);
        Class<? extends MqMessage> found = registry.find(DemoMqMessage.class.getName());

        log.info("[测试断言] 场景=重复注册同类型 | found={} | 期望不抛异常", found);
        assertThat(found).isEqualTo(DemoMqMessage.class);
    }
}
