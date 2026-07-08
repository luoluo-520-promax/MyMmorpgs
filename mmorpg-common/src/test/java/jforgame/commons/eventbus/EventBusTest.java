/**
 * 文件说明：EventBus 单元测试。
 * 职责：验证注册订阅者与 publish 派发行为。
 */
package jforgame.commons.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class EventBusTest {

    private static final Logger log = LoggerFactory.getLogger(EventBusTest.class);

    public static final class DemoEvent implements BaseEvent {
        private final Object owner;

        public DemoEvent(Object owner) {
            this.owner = owner;
        }

        @Override
        public Object getOwner() {
            return owner;
        }
    }

    public static class Listener {
        private final CountDownLatch latch = new CountDownLatch(1);
        volatile int hits;

        @Subscribe
        public void onDemo(DemoEvent e) {
            hits++;
            latch.countDown();
        }
    }

    @Test
    public void registerAndPublish_invokesSubscriber() throws Exception {
        String owner = "scope-player-001";
        EventBus bus = new EventBus();
        Listener listener = new Listener();
        bus.register(listener);
        log.info("[测试开始] 场景=注册并发布 | eventOwner={} | listenerClass={}",
                owner, listener.getClass().getSimpleName());

        bus.publish(new DemoEvent(owner));

        boolean done = listener.latch.await(3, TimeUnit.SECONDS);
        log.info("[测试断言] 场景=注册并发布 | done={} | hits={} | 期望hits=1", done, listener.hits);
        assertThat(done).isTrue();
        assertThat(listener.hits).isEqualTo(1);
    }

    @Test
    public void publishNull_isNoOp() {
        log.info("[测试开始] 场景=发布null事件 | event=null");

        EventBus bus = new EventBus();
        bus.register(new Listener());
        bus.publish(null);

        log.info("[测试断言] 场景=发布null事件 | 期望=静默忽略不抛异常");
    }
}
