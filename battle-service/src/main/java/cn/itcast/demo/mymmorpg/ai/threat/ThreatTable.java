package cn.itcast.demo.mymmorpg.ai.threat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仇恨表：伤害加权 + 衰减 + 房主压制可选。实时战斗仅用规则表，禁止 LLM。
 */
public final class ThreatTable {

    public enum PriorityMode {
        ATTACKER,
        HOST,
        DAMAGE_WEIGHTED
    }

    public record ThreatEntry(long playerId, double threat, long lastHitAtMs) {
    }

    private final ConcurrentHashMap<Long, Double> threat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastHit = new ConcurrentHashMap<>();
    private volatile PriorityMode mode = PriorityMode.DAMAGE_WEIGHTED;
    private volatile long hostPlayerId;
    private volatile double hostBias = 1.25;
    private volatile double decayPerSecond = 0.02;

    public void configure(PriorityMode mode, long hostPlayerId, double hostBias) {
        if (mode != null) {
            this.mode = mode;
        }
        this.hostPlayerId = hostPlayerId;
        this.hostBias = Math.max(1.0, hostBias);
    }

    public void addDamage(long playerId, long damage, long nowMs) {
        if (playerId <= 0 || damage <= 0) {
            return;
        }
        double add = damage;
        if (playerId == hostPlayerId) {
            add *= hostBias;
        }
        threat.merge(playerId, add, Double::sum);
        lastHit.put(playerId, nowMs);
    }

    public void tickDecay(long nowMs) {
        if (decayPerSecond <= 0) {
            return;
        }
        for (Map.Entry<Long, Long> e : lastHit.entrySet()) {
            long idleMs = nowMs - e.getValue();
            if (idleMs < 1_000L) {
                continue;
            }
            double factor = Math.max(0.0, 1.0 - decayPerSecond * (idleMs / 1_000.0));
            threat.computeIfPresent(e.getKey(), (id, v) -> v * factor <= 1.0 ? null : v * factor);
        }
    }

    public long topThreatTarget(long lastAttackerId) {
        return switch (mode) {
            case HOST -> hostPlayerId > 0 ? hostPlayerId : lastAttackerId;
            case ATTACKER -> lastAttackerId > 0 ? lastAttackerId : topByDamage();
            case DAMAGE_WEIGHTED -> {
                long top = topByDamage();
                yield top > 0 ? top : lastAttackerId;
            }
        };
    }

    public List<ThreatEntry> snapshot(long nowMs) {
        List<ThreatEntry> list = new ArrayList<>();
        for (Map.Entry<Long, Double> e : threat.entrySet()) {
            list.add(new ThreatEntry(e.getKey(), e.getValue(), lastHit.getOrDefault(e.getKey(), 0L)));
        }
        list.sort(Comparator.comparingDouble(ThreatEntry::threat).reversed());
        return list;
    }

    public Map<String, Object> toView(long nowMs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", mode.name());
        m.put("hostPlayerId", hostPlayerId);
        m.put("hostBias", hostBias);
        m.put("top", topThreatTarget(0L));
        List<Map<String, Object>> board = new ArrayList<>();
        for (ThreatEntry e : snapshot(nowMs)) {
            board.add(Map.of("playerId", e.playerId(), "threat", e.threat(), "lastHitAtMs", e.lastHitAtMs()));
        }
        m.put("board", board);
        return m;
    }

    public void clear() {
        threat.clear();
        lastHit.clear();
    }

    private long topByDamage() {
        long bestId = 0L;
        double best = -1.0;
        for (Map.Entry<Long, Double> e : threat.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                bestId = e.getKey();
            }
        }
        return bestId;
    }
}
