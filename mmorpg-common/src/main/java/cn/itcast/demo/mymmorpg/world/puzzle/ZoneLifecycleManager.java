package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 持续效果区生命周期：注册 → Tick 伤害 → 到期销毁。
 */
@Service
public class ZoneLifecycleManager {

    public enum ShapeKind {
        CIRCLE, RECT
    }

    /**
     * 持续伤害 / 状态区域（燃烧草地、冻结平台等）。
     */
    public record PersistentEffectZone(
            String zoneId,
            int worldId,
            ShapeKind shape,
            float centerX,
            float centerZ,
            float radiusOrHalfW,
            float halfDepth,
            long createdAtMs,
            long expireAtMs,
            long tickIntervalMs,
            /** 每次 tick 的基础伤害；可被 DamageFormula 覆盖语义 */
            int baseDamage,
            String damageFormula,
            String sourceKind,
            Map<String, Object> meta) {

        public PersistentEffectZone {
            zoneId = zoneId == null || zoneId.isBlank() ? UUID.randomUUID().toString() : zoneId;
            shape = shape == null ? ShapeKind.CIRCLE : shape;
            radiusOrHalfW = radiusOrHalfW <= 0f ? 4f : radiusOrHalfW;
            halfDepth = halfDepth <= 0f ? radiusOrHalfW : halfDepth;
            tickIntervalMs = tickIntervalMs <= 0 ? 500L : tickIntervalMs;
            baseDamage = Math.max(0, baseDamage);
            damageFormula = damageFormula == null || damageFormula.isBlank()
                    ? "flat:" + baseDamage : damageFormula;
            sourceKind = sourceKind == null ? "GENERIC" : sourceKind;
            meta = meta == null ? Map.of() : Map.copyOf(meta);
        }

        public boolean contains(float x, float z) {
            if (shape == ShapeKind.RECT) {
                return Math.abs(x - centerX) <= radiusOrHalfW && Math.abs(z - centerZ) <= halfDepth;
            }
            float dx = x - centerX;
            float dz = z - centerZ;
            return dx * dx + dz * dz <= radiusOrHalfW * radiusOrHalfW;
        }

        public int damageAtTick() {
            // 简易公式：flat:N / intensity:N*k
            if (damageFormula.startsWith("flat:")) {
                try {
                    return Integer.parseInt(damageFormula.substring(5).trim());
                } catch (Exception e) {
                    return baseDamage;
                }
            }
            if (damageFormula.startsWith("burn:")) {
                try {
                    int n = Integer.parseInt(damageFormula.substring(5).trim());
                    return Math.max(1, n);
                } catch (Exception e) {
                    return Math.max(1, baseDamage);
                }
            }
            return Math.max(0, baseDamage);
        }
    }

    public record TickHit(String zoneId, long entityId, int damage, String sourceKind) {
    }

    public static final long DOT_EXIT_BUFFER_MS = 300L;

    public record DotExitBuffer(long entityId, String zoneId, long exitAtMs, long bufferUntilMs) {
    }

    private final ConcurrentHashMap<String, PersistentEffectZone> zones = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastTickAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, DotExitBuffer> exitBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> entityInZone = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);
    private BiConsumer<PersistentEffectZone, List<TickHit>> onTick;

    /**
     * 实体离开元素区时下发 DOT_EXIT_BUFFER，客户端 300ms 内停止扣血闪烁。
     */
    public Map<String, Object> onEntityLeaveZone(long entityId, String zoneId, long nowMs) {
        DotExitBuffer buf = new DotExitBuffer(entityId, zoneId, nowMs, nowMs + DOT_EXIT_BUFFER_MS);
        exitBuffers.put(entityId, buf);
        entityInZone.remove(entityId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "DOT_EXIT_BUFFER");
        body.put("entityId", entityId);
        body.put("zoneId", zoneId);
        body.put("bufferMs", DOT_EXIT_BUFFER_MS);
        body.put("bufferUntilMs", buf.bufferUntilMs());
        body.put("suppressClientDamageVfx", true);
        body.put("serverSettlesInBackground", true);
        return body;
    }

    public boolean isInExitBuffer(long entityId, long nowMs) {
        DotExitBuffer buf = exitBuffers.get(entityId);
        if (buf == null) {
            return false;
        }
        if (nowMs > buf.bufferUntilMs()) {
            exitBuffers.remove(entityId);
            return false;
        }
        return true;
    }

    /**
     * 检测实体位置变化，自动触发离开事件。
     */
    public List<Map<String, Object>> trackEntityZones(
            Map<Long, float[]> entityPositions, int worldId, long nowMs) {
        List<Map<String, Object>> leaves = new ArrayList<>();
        if (entityPositions == null) {
            return leaves;
        }
        for (Map.Entry<Long, float[]> e : entityPositions.entrySet()) {
            long entityId = e.getKey();
            float[] p = e.getValue();
            if (p == null || p.length < 2) {
                continue;
            }
            String currentZone = findZoneAt(worldId, p[0], p[1]);
            String prev = entityInZone.get(entityId);
            if (prev != null && !prev.equals(currentZone)) {
                leaves.add(onEntityLeaveZone(entityId, prev, nowMs));
            }
            if (currentZone != null) {
                entityInZone.put(entityId, currentZone);
            } else {
                entityInZone.remove(entityId);
            }
        }
        return leaves;
    }

    private String findZoneAt(int worldId, float x, float z) {
        for (PersistentEffectZone zone : zones.values()) {
            if (zone.worldId() == worldId && zone.contains(x, z)) {
                return zone.zoneId();
            }
        }
        return null;
    }

    public void setOnTick(BiConsumer<PersistentEffectZone, List<TickHit>> listener) {
        this.onTick = listener;
    }

    public Map<String, Object> register(PersistentEffectZone zone) {
        if (zone == null) {
            return Map.of("ok", false, "error", "zone_required");
        }
        zones.put(zone.zoneId(), zone);
        lastTickAt.put(zone.zoneId(), zone.createdAtMs());
        Map<String, Object> body = new LinkedHashMap<>(toView(zone));
        body.put("ok", true);
        return body;
    }

    /** 便捷：燃烧 DoT 区（默认 3s / 0.5s tick）。 */
    public Map<String, Object> spawnBurnZone(
            int worldId, float x, float z, float radius, int damagePerTick, long nowMs) {
        String id = "burn-" + seq.getAndIncrement();
        PersistentEffectZone zone = new PersistentEffectZone(
                id, worldId, ShapeKind.CIRCLE, x, z, radius, radius,
                nowMs, nowMs + 3_000L, 500L, damagePerTick, "burn:" + damagePerTick,
                "BURN", Map.of("reaction", "BURNING"));
        return register(zone);
    }

    /**
     * 对给定实体坐标做一次全局 tick；返回命中列表，并清理过期 Zone。
     */
    public Map<String, Object> tick(int worldId, Map<Long, float[]> entityPositions, long nowMs) {
        purgeExpired(nowMs);
        List<Map<String, Object>> hits = new ArrayList<>();
        List<String> destroyed = new ArrayList<>();
        for (PersistentEffectZone zone : zones.values()) {
            if (zone.worldId() != worldId) {
                continue;
            }
            if (zone.expireAtMs() <= nowMs) {
                destroyed.add(zone.zoneId());
                continue;
            }
            long last = lastTickAt.getOrDefault(zone.zoneId(), zone.createdAtMs());
            if (nowMs - last < zone.tickIntervalMs()) {
                continue;
            }
            lastTickAt.put(zone.zoneId(), nowMs);
            List<TickHit> zoneHits = new ArrayList<>();
            if (entityPositions != null) {
                for (Map.Entry<Long, float[]> e : entityPositions.entrySet()) {
                    float[] p = e.getValue();
                    if (p == null || p.length < 2) {
                        continue;
                    }
                    if (isInExitBuffer(e.getKey(), nowMs)) {
                        continue;
                    }
                    if (zone.contains(p[0], p.length > 1 ? p[1] : 0f)) {
                        int dmg = zone.damageAtTick();
                        TickHit hit = new TickHit(zone.zoneId(), e.getKey(), dmg, zone.sourceKind());
                        zoneHits.add(hit);
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("zoneId", hit.zoneId());
                        row.put("entityId", hit.entityId());
                        row.put("damage", hit.damage());
                        row.put("sourceKind", hit.sourceKind());
                        hits.add(row);
                    }
                }
            }
            if (onTick != null && !zoneHits.isEmpty()) {
                onTick.accept(zone, zoneHits);
            }
        }
        for (String id : destroyed) {
            zones.remove(id);
            lastTickAt.remove(id);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("worldId", worldId);
        body.put("hits", hits);
        body.put("hitCount", hits.size());
        body.put("destroyed", destroyed);
        body.put("activeZones", zones.values().stream()
                .filter(z -> z.worldId() == worldId)
                .map(this::toView)
                .toList());
        return body;
    }

    public Map<String, Object> destroy(String zoneId) {
        PersistentEffectZone removed = zones.remove(zoneId);
        lastTickAt.remove(zoneId);
        return Map.of("ok", removed != null, "zoneId", zoneId == null ? "" : zoneId);
    }

    public List<Map<String, Object>> listActive(int worldId, long nowMs) {
        purgeExpired(nowMs);
        return zones.values().stream()
                .filter(z -> z.worldId() == worldId)
                .map(this::toView)
                .toList();
    }

    private void purgeExpired(long nowMs) {
        Iterator<Map.Entry<String, PersistentEffectZone>> it = zones.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PersistentEffectZone> e = it.next();
            if (e.getValue().expireAtMs() <= nowMs) {
                it.remove();
                lastTickAt.remove(e.getKey());
            }
        }
    }

    private Map<String, Object> toView(PersistentEffectZone z) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("zoneId", z.zoneId());
        m.put("worldId", z.worldId());
        m.put("shape", z.shape().name());
        m.put("centerX", z.centerX());
        m.put("centerZ", z.centerZ());
        m.put("radiusOrHalfW", z.radiusOrHalfW());
        m.put("halfDepth", z.halfDepth());
        m.put("expireAtMs", z.expireAtMs());
        m.put("tickIntervalMs", z.tickIntervalMs());
        m.put("baseDamage", z.baseDamage());
        m.put("damageFormula", z.damageFormula());
        m.put("sourceKind", z.sourceKind());
        m.put("meta", z.meta());
        return m;
    }
}
