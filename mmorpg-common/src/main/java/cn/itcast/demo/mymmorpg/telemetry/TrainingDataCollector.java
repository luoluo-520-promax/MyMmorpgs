package cn.itcast.demo.mymmorpg.telemetry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * 离线训练数据埋点缓冲：操作序列、战斗结果、充值行为；可定期 flush 到数据湖。
 */
public final class TrainingDataCollector {

    public record Event(String category, long playerId, long atMs, Map<String, Object> payload) {
    }

    private final ConcurrentLinkedQueue<Event> buffer = new ConcurrentLinkedQueue<>();
    private final LongAdder accepted = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final int maxBuffer;

    public TrainingDataCollector() {
        this(10_000);
    }

    public TrainingDataCollector(int maxBuffer) {
        this.maxBuffer = Math.max(1, maxBuffer);
    }

    public void collect(String category, long playerId, Map<String, Object> payload) {
        if (buffer.size() >= maxBuffer) {
            dropped.increment();
            return;
        }
        Map<String, Object> copy = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        buffer.offer(new Event(category == null ? "unknown" : category, playerId,
                System.currentTimeMillis(), copy));
        accepted.increment();
    }

    public List<Event> flush(int limit) {
        int n = Math.max(1, limit);
        List<Event> out = new ArrayList<>(Math.min(n, buffer.size()));
        for (int i = 0; i < n; i++) {
            Event e = buffer.poll();
            if (e == null) {
                break;
            }
            out.add(e);
        }
        return out;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buffered", buffer.size());
        m.put("accepted", accepted.sum());
        m.put("dropped", dropped.sum());
        m.put("maxBuffer", maxBuffer);
        return m;
    }
}
