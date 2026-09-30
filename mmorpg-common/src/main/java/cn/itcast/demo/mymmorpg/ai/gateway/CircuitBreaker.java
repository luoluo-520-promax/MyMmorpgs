package cn.itcast.demo.mymmorpg.ai.gateway;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简易熔断器：连续失败达阈值后开启，冷却后半开探测。
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMs;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();
    private volatile State state = State.CLOSED;

    public CircuitBreaker() {
        this(5, 30_000L);
    }

    public CircuitBreaker(int failureThreshold, long openMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openMs = Math.max(1_000L, openMs);
    }

    public boolean allow() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt.get() >= openMs) {
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    public void onSuccess() {
        failures.set(0);
        state = State.CLOSED;
    }

    public void onFailure() {
        int f = failures.incrementAndGet();
        if (f >= failureThreshold || state == State.HALF_OPEN) {
            state = State.OPEN;
            openedAt.set(System.currentTimeMillis());
        }
    }

    public State state() {
        return state;
    }

    public int failures() {
        return failures.get();
    }
}
