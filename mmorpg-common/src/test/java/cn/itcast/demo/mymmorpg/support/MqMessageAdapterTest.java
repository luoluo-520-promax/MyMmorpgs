/**
 * 文件说明：MQ 消息异步适配层单元测试。
 * 职责：验证 MqMessageAdapter 对无 receiver 消息的过滤与异步派发。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class MqMessageAdapterTest {

    private static final Logger log = LoggerFactory.getLogger(MqMessageAdapterTest.class);

    private MqMessageDispatcher dispatcher;
    private MqMessageAdapter adapter;

    @BeforeMethod
    public void setUp() {
        dispatcher = new MqMessageDispatcher();
        adapter = new MqMessageAdapter(dispatcher);
        log.info("[测试前置] MqMessageAdapter 已加载");
    }

    @AfterMethod
    public void tearDown() {
        adapter.shutdown();
    }

    static final class RewardMqMessage implements MqMessage {
        private final String receiver;

        RewardMqMessage(String receiver) {
            this.receiver = receiver;
        }

        @Override
        public String receiver() {
            return receiver;
        }
    }

    @Test
    public void submit_dispatchesToHandler() throws Exception {
        String receiver = "player-service-01";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger hits = new AtomicInteger();
        dispatcher.registerHandler(new MqMessageHandler<RewardMqMessage>() {
            @Override
            public Class<RewardMqMessage> messageType() {
                return RewardMqMessage.class;
            }

            @Override
            public String receiver() {
                return receiver;
            }

            @Override
            public void handle(RewardMqMessage message) {
                hits.incrementAndGet();
                latch.countDown();
            }
        });
        log.info("[测试开始] 场景=异步派发 | messageType={} | receiver={}",
                RewardMqMessage.class.getSimpleName(), receiver);

        adapter.submit(new RewardMqMessage(receiver));

        boolean done = latch.await(3, TimeUnit.SECONDS);
        log.info("[测试断言] 场景=异步派发 | done={} | hits={} | 期望hits=1", done, hits.get());
        assertThat(done).isTrue();
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    public void submit_blankReceiver_isIgnored() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        dispatcher.registerHandler(new MqMessageHandler<RewardMqMessage>() {
            @Override
            public Class<RewardMqMessage> messageType() {
                return RewardMqMessage.class;
            }

            @Override
            public String receiver() {
                return "player-service";
            }

            @Override
            public void handle(RewardMqMessage message) {
                latch.countDown();
            }
        });
        log.info("[测试开始] 场景=无receiver忽略 | receiver=blank");

        adapter.submit(new RewardMqMessage("   "));

        boolean done = latch.await(500, TimeUnit.MILLISECONDS);
        log.info("[测试断言] 场景=无receiver忽略 | handlerInvoked={} | 期望=false", done);
        assertThat(done).isFalse();
    }
}
