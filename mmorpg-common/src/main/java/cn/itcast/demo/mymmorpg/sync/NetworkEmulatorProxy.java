package cn.itcast.demo.mymmorpg.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chaos Mesh for Session：集成测试阶段随机丢弃 SceneMoveCmd ACK 或延迟回包。
 */
public class NetworkEmulatorProxy {

    public enum FaultKind {
        DROP_ACK, DELAY_ACK, PASSTHROUGH
    }

    public record EmulatedResult(
            FaultKind kind,
            boolean delivered,
            long delayMs,
            Object payload) {
    }

    private volatile double dropRate = 0.1;
    private volatile double delayRate = 0.2;
    private volatile long delayMs = 2_000L;
    private final AtomicInteger dropped = new AtomicInteger();
    private final AtomicInteger delayed = new AtomicInteger();
    private final AtomicInteger passed = new AtomicInteger();
    private final List<Map<String, Object>> log = new ArrayList<>();

    public void configure(double dropRate, double delayRate, long delayMs) {
        this.dropRate = Math.max(0, Math.min(1, dropRate));
        this.delayRate = Math.max(0, Math.min(1, delayRate));
        this.delayMs = Math.max(0, delayMs);
    }

    public EmulatedResult emulateAck(String cmdId, Object payload) {
        double r = ThreadLocalRandom.current().nextDouble();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("cmdId", cmdId);
        if (r < dropRate) {
            dropped.incrementAndGet();
            entry.put("kind", FaultKind.DROP_ACK.name());
            synchronized (log) {
                log.add(entry);
            }
            return new EmulatedResult(FaultKind.DROP_ACK, false, 0L, null);
        }
        if (r < dropRate + delayRate) {
            delayed.incrementAndGet();
            entry.put("kind", FaultKind.DELAY_ACK.name());
            entry.put("delayMs", delayMs);
            synchronized (log) {
                log.add(entry);
            }
            return new EmulatedResult(FaultKind.DELAY_ACK, true, delayMs, payload);
        }
        passed.incrementAndGet();
        entry.put("kind", FaultKind.PASSTHROUGH.name());
        synchronized (log) {
            log.add(entry);
        }
        return new EmulatedResult(FaultKind.PASSTHROUGH, true, 0L, payload);
    }

    /** 确定性故障注入（单测用）。 */
    public EmulatedResult force(FaultKind kind, String cmdId, Object payload) {
        if (kind == FaultKind.DROP_ACK) {
            dropped.incrementAndGet();
            return new EmulatedResult(FaultKind.DROP_ACK, false, 0L, null);
        }
        if (kind == FaultKind.DELAY_ACK) {
            delayed.incrementAndGet();
            return new EmulatedResult(FaultKind.DELAY_ACK, true, delayMs, payload);
        }
        passed.incrementAndGet();
        return new EmulatedResult(FaultKind.PASSTHROUGH, true, 0L, payload);
    }

    public Map<String, Object> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("dropped", dropped.get());
        body.put("delayed", delayed.get());
        body.put("passed", passed.get());
        body.put("dropRate", dropRate);
        body.put("delayRate", delayRate);
        body.put("delayMs", delayMs);
        return body;
    }
}
