package cn.itcast.demo.mymmorpg.world.debug;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实体 Debug TraceId：贯穿出生→移动→攻击→死亡，便于排查「怪物凭空消失」。
 */
@Component
public class EntityDebugTrace {

    public enum Phase {
        SPAWN, ENTER, MOVE, ATTACK, DEATH, DESPAWN, MIGRATE
    }

    public record TraceEvent(String traceId, long entityId, Phase phase, String detail, long atMs) {
    }

    private final ConcurrentHashMap<Long, String> entityTrace = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<TraceEvent>> timelines =
            new ConcurrentHashMap<>();
    private final AtomicLong events = new AtomicLong();
    private volatile int maxEventsPerEntity = 64;

    public String assign(long entityId) {
        return entityTrace.computeIfAbsent(entityId, id ->
                "tr-" + Long.toHexString(id) + "-"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    public String traceIdOf(long entityId) {
        return entityTrace.get(entityId);
    }

    public void record(long entityId, Phase phase, String detail, long nowMs) {
        String tid = assign(entityId);
        TraceEvent ev = new TraceEvent(tid, entityId, phase, detail == null ? "" : detail, nowMs);
        CopyOnWriteArrayList<TraceEvent> list = timelines.computeIfAbsent(tid, k -> new CopyOnWriteArrayList<>());
        list.add(ev);
        while (list.size() > maxEventsPerEntity) {
            list.remove(0);
        }
        events.incrementAndGet();
    }

    public List<TraceEvent> timeline(long entityId) {
        String tid = entityTrace.get(entityId);
        if (tid == null) {
            return List.of();
        }
        CopyOnWriteArrayList<TraceEvent> list = timelines.get(tid);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void clear(long entityId) {
        String tid = entityTrace.remove(entityId);
        if (tid != null) {
            timelines.remove(tid);
        }
    }

    public Map<String, Object> view(long entityId) {
        Map<String, Object> m = new LinkedHashMap<>();
        String tid = entityTrace.get(entityId);
        m.put("entityId", entityId);
        m.put("traceId", tid == null ? "" : tid);
        List<Map<String, Object>> evs = new ArrayList<>();
        for (TraceEvent e : timeline(entityId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phase", e.phase().name());
            row.put("detail", e.detail());
            row.put("atMs", e.atMs());
            evs.add(row);
        }
        m.put("events", evs);
        return m;
    }

    public Map<String, Object> stats() {
        return Map.of(
                "trackedEntities", entityTrace.size(),
                "totalEvents", events.get(),
                "maxEventsPerEntity", maxEventsPerEntity);
    }
}
