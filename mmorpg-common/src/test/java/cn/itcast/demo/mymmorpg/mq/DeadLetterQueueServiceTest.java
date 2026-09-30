package cn.itcast.demo.mymmorpg.mq;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class DeadLetterQueueServiceTest {

    private DeadLetterQueueService dlq;

    @BeforeMethod
    public void setUp() {
        dlq = new DeadLetterQueueService(null);
        dlq.clear();
    }

    @Test
    public void enqueueListAndMetrics() {
        dlq.enqueue("shop.paid", "m1", "payload-1", "timeout", 2);
        dlq.enqueue("shop.paid", "m2", "payload-2", "npe", 1);

        assertThat(dlq.depth()).isEqualTo(2);
        assertThat(dlq.oldestAgeMs()).isGreaterThanOrEqualTo(0L);
        assertThat(dlq.metrics()).containsKeys("depth", "oldestAgeMs", "enqueueTotal");

        List<DeadLetterQueueService.DeadLetterMessage> listed = dlq.list(10);
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).messageId()).isIn("m1", "m2");
    }

    @Test
    public void retryRemovesAndReturns() {
        dlq.enqueue("battle.ended", "retry-1", "{}", "fail", 3);
        Optional<DeadLetterQueueService.DeadLetterMessage> msg = dlq.retry("retry-1");
        assertThat(msg).isPresent();
        assertThat(msg.get().topic()).isEqualTo("battle.ended");
        assertThat(dlq.depth()).isZero();
        assertThat(dlq.retry("retry-1")).isEmpty();
    }

    @Test
    public void discardRemoves() {
        dlq.enqueue("t", "d1", "p", "e", 1);
        assertThat(dlq.discard("d1")).isTrue();
        assertThat(dlq.discard("d1")).isFalse();
        assertThat(dlq.depth()).isZero();
        assertThat(dlq.oldestAgeMs()).isZero();
    }
}
