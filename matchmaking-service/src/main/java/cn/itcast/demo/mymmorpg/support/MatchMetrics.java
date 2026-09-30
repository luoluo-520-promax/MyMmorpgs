package cn.itcast.demo.mymmorpg.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 匹配可观测性：排队人数、成功/失败计数，供高峰期扩容决策。
 */
@Component
public class MatchMetrics {

    private final Counter enqueueAttempts;
    private final Counter matchSuccess;
    private final Counter matchTimeout;
    private final Counter matchCancel;
    private final Counter reserveFail;
    private final AtomicInteger waitingCount = new AtomicInteger();

    public MatchMetrics(MeterRegistry registry) {
        this.enqueueAttempts = Counter.builder("matchmaking.match.attempts").register(registry);
        this.matchSuccess = Counter.builder("matchmaking.match.success").register(registry);
        this.matchTimeout = Counter.builder("matchmaking.match.timeout").register(registry);
        this.matchCancel = Counter.builder("matchmaking.match.cancel").register(registry);
        this.reserveFail = Counter.builder("matchmaking.match.reserve_fail").register(registry);
        Gauge.builder("matchmaking.queue.waiting_count", waitingCount, AtomicInteger::get)
                .register(registry);
        Gauge.builder("matchmaking.match.success_rate", this, MatchMetrics::successRate)
                .register(registry);
    }

    public void recordEnqueue() {
        enqueueAttempts.increment();
        waitingCount.incrementAndGet();
    }

    public void recordSuccess() {
        matchSuccess.increment();
        waitingCount.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void recordSuccessParty(int partySize) {
        matchSuccess.increment();
        waitingCount.updateAndGet(v -> Math.max(0, v - Math.max(0, partySize)));
    }

    public void recordTimeout() {
        matchTimeout.increment();
        waitingCount.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void recordCancel() {
        matchCancel.increment();
        waitingCount.updateAndGet(v -> Math.max(0, v - 1));
    }

    public void recordReserveFail() {
        reserveFail.increment();
    }

    public void setWaitingCount(int count) {
        waitingCount.set(Math.max(0, count));
    }

    private double successRate() {
        double attempts = enqueueAttempts.count();
        if (attempts <= 0) {
            return 1.0;
        }
        return matchSuccess.count() / attempts;
    }
}
