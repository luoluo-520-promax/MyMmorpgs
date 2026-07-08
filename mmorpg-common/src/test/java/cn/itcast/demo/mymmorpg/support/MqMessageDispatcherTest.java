/**
 * 文件说明：MQ 消息同步分发器单元测试。
 * 职责：验证 MqMessageDispatcher 按 receiver 路由到正确 Handler。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MqMessageDispatcherTest {

    private static final Logger log = LoggerFactory.getLogger(MqMessageDispatcherTest.class);

    private MqMessageDispatcher dispatcher;

    @BeforeMethod
    public void setUp() {
        dispatcher = new MqMessageDispatcher();
        log.info("[测试前置] MqMessageDispatcher 已加载");
    }

    static final class PlayerKickMqMessage implements MqMessage {
        private final String receiver;

        PlayerKickMqMessage(String receiver) {
            this.receiver = receiver;
        }

        @Override
        public String receiver() {
            return receiver;
        }
    }

    @Test
    public void dispatch_invokesMatchingHandler() {
        String targetReceiver = "player-service-01";
        AtomicInteger hits = new AtomicInteger();
        dispatcher.registerHandler(new MqMessageHandler<PlayerKickMqMessage>() {
            @Override
            public Class<PlayerKickMqMessage> messageType() {
                return PlayerKickMqMessage.class;
            }

            @Override
            public String receiver() {
                return targetReceiver;
            }

            @Override
            public void handle(PlayerKickMqMessage message) {
                hits.incrementAndGet();
            }
        });
        PlayerKickMqMessage message = new PlayerKickMqMessage(targetReceiver);
        log.info("[测试开始] 场景=匹配receiver派发 | messageType={} | receiver={}",
                message.getClass().getSimpleName(), targetReceiver);

        dispatcher.dispatch(message);

        log.info("[测试断言] 场景=匹配receiver派发 | hits={} | 期望=1", hits.get());
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    public void dispatch_skipsMismatchedReceiver() {
        String handlerReceiver = "player-service-01";
        String messageReceiver = "player-service-02";
        AtomicInteger hits = new AtomicInteger();
        dispatcher.registerHandler(new MqMessageHandler<PlayerKickMqMessage>() {
            @Override
            public Class<PlayerKickMqMessage> messageType() {
                return PlayerKickMqMessage.class;
            }

            @Override
            public String receiver() {
                return handlerReceiver;
            }

            @Override
            public void handle(PlayerKickMqMessage message) {
                hits.incrementAndGet();
            }
        });
        log.info("[测试开始] 场景=receiver不匹配 | handlerReceiver={} | messageReceiver={}",
                handlerReceiver, messageReceiver);

        dispatcher.dispatch(new PlayerKickMqMessage(messageReceiver));

        log.info("[测试断言] 场景=receiver不匹配 | hits={} | 期望=0", hits.get());
        assertThat(hits.get()).isZero();
    }

    @Test
    public void registerHandler_duplicate_throws() {
        MqMessageHandler<PlayerKickMqMessage> handler = new MqMessageHandler<PlayerKickMqMessage>() {
            @Override
            public Class<PlayerKickMqMessage> messageType() {
                return PlayerKickMqMessage.class;
            }

            @Override
            public String receiver() {
                return "player-service";
            }

            @Override
            public void handle(PlayerKickMqMessage message) {
            }
        };
        dispatcher.registerHandler(handler);
        log.info("[测试开始] 场景=重复注册Handler | messageType={}", PlayerKickMqMessage.class.getName());

        assertThatThrownBy(() -> dispatcher.registerHandler(handler))
                .isInstanceOf(IllegalStateException.class);
        log.info("[测试断言] 场景=重复注册Handler | 期望=抛出IllegalStateException");
    }
}
