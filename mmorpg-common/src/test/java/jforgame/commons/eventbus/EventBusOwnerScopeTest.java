/**
 * 文件说明：EventBus owner 作用域过滤单元测试。
 * 职责：验证 OwnerScopedSubscriber 只接收匹配 owner 的事件。
 */
package jforgame.commons.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class EventBusOwnerScopeTest {

    private static final Logger log = LoggerFactory.getLogger(EventBusOwnerScopeTest.class);

    public static final class ScopedEvent implements BaseEvent {
        private final Object owner;

        public ScopedEvent(Object owner) {
            this.owner = owner;
        }

        @Override
        public Object getOwner() {
            return owner;
        }
    }

    public static class ScopedListener implements OwnerScopedSubscriber {
        private final Object scopeOwner;
        private final AtomicInteger hits = new AtomicInteger();
        private final CountDownLatch latch = new CountDownLatch(1);

        ScopedListener(Object scopeOwner) {
            this.scopeOwner = scopeOwner;
        }

        @Override
        public Object getOwnerScope() {
            return scopeOwner;
        }

        @Subscribe
        public void onScoped(ScopedEvent event) {
            hits.incrementAndGet();
            latch.countDown();
        }
    }

    @Test
    public void publish_deliversOnlyMatchingOwner() throws Exception {
        String matchedOwner = "server-1001";
        String otherOwner = "server-2002";
        EventBus bus = new EventBus();
        ScopedListener matchedListener = new ScopedListener(matchedOwner);
        ScopedListener otherListener = new ScopedListener(otherOwner);
        bus.register(matchedListener);
        bus.register(otherListener);
        log.info("[测试开始] 场景=owner作用域过滤 | eventOwner={} | listenerScopes=[{},{}]",
                matchedOwner, matchedOwner, otherOwner);

        bus.publish(new ScopedEvent(matchedOwner));

        assertThat(matchedListener.latch.await(3, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        log.info("[测试断言] 场景=owner作用域过滤 | matchedHits={} | otherHits={} | 期望=[1,0]",
                matchedListener.hits.get(), otherListener.hits.get());
        assertThat(matchedListener.hits.get()).isEqualTo(1);
        assertThat(otherListener.hits.get()).isZero();
    }
}
