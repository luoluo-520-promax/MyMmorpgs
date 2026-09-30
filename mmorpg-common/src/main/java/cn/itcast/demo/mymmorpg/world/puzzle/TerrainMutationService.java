package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地貌改写：元素反应写入区域网格（感电水池 / 冰封河面）。
 */
@Service
public class TerrainMutationService {

    public static final String TERRAIN_REACTION = "TERRAIN_REACTION";
    public static final String WATER = "WATER";
    public static final String ELECTRO_CHARGED_ZONE = "ELECTRO_CHARGED_ZONE";
    public static final String ICE_TERRAIN = "ICE_TERRAIN";

    public enum CellMark {
        PLAIN, WATER, ELECTRO_CHARGED_ZONE, ICE_TERRAIN
    }

    private final ConcurrentHashMap<String, CellMark> cells = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> expireAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Float> humidity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> iceSpreadCurves = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> icePhysicsLockAt = new ConcurrentHashMap<>();
    private TerrainStateVector terrainStateVector;

    public static final String ICE_SPREAD_CRV = "ICE_SPREAD_CRV";
    public static final long ICE_PHYSICS_DELAY_MS = 2_000L;

    public void bindTerrainStateVector(TerrainStateVector tsv) {
        this.terrainStateVector = tsv;
    }

    private static String cellKey(String regionId, int gx, int gz) {
        return regionId + ":" + gx + ":" + gz;
    }

    public void markWater(String regionId, int gx, int gz) {
        cells.put(cellKey(regionId, gx, gz), CellMark.WATER);
    }

    public void setHumidity(String regionId, float percent) {
        humidity.put(regionId == null ? "" : regionId.trim(), Math.max(0f, Math.min(100f, percent)));
    }

    /**
     * 水池 OVERLOAD（雷+火）→ 全水池 ELECTRO_CHARGED_ZONE 10s，进入者感电传导。
     */
    public Map<String, Object> applyOverloadOnWater(String regionId, int gx, int gz, long nowMs) {
        String seed = cellKey(regionId, gx, gz);
        if (cells.get(seed) != CellMark.WATER && cells.get(seed) != CellMark.ELECTRO_CHARGED_ZONE) {
            return Map.of("ok", false, "error", "not_water_cell");
        }
        List<String> changed = new ArrayList<>();
        String prefix = (regionId == null ? "" : regionId.trim()) + ":";
        for (Map.Entry<String, CellMark> e : cells.entrySet()) {
            if (e.getKey().startsWith(prefix)
                    && (e.getValue() == CellMark.WATER || e.getValue() == CellMark.ELECTRO_CHARGED_ZONE)) {
                cells.put(e.getKey(), CellMark.ELECTRO_CHARGED_ZONE);
                expireAt.put(e.getKey(), nowMs + 10_000L);
                changed.add(e.getKey());
                if (terrainStateVector != null) {
                    String[] parts = e.getKey().split(":");
                    if (parts.length >= 3) {
                        try {
                            terrainStateVector.bump(parts[0], Integer.parseInt(parts[1]),
                                    Integer.parseInt(parts[2]), ELECTRO_CHARGED_ZONE, nowMs);
                        } catch (NumberFormatException ignored) {
                            // key 格式异常时跳过 revision bump
                        }
                    }
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", TERRAIN_REACTION);
        body.put("reaction", "OVERLOAD");
        body.put("zone", ELECTRO_CHARGED_ZONE);
        body.put("durationMs", 10_000);
        body.put("cells", changed);
        body.put("chainRadiusM", 5);
        body.put("effect", "ELECTRO_CONDUCT");
        if (terrainStateVector != null) {
            body.put("stateRevision", terrainStateVector.revisionOf(regionId, gx, gz));
            body.put("tsvSynced", true);
        }
        return body;
    }

    /**
     * 冰+水且湿度&gt;60% → ICE_TERRAIN 30s，移速×0.7 / 滑行×1.3，可承重压板。
     */
    public Map<String, Object> applyFreezeOnWater(String regionId, int gx, int gz, long nowMs) {
        float h = humidity.getOrDefault(regionId == null ? "" : regionId.trim(), 0f);
        if (h <= 60f) {
            return Map.of("ok", false, "error", "humidity_too_low", "humidity", h);
        }
        String seed = cellKey(regionId, gx, gz);
        if (cells.get(seed) != CellMark.WATER && cells.get(seed) != CellMark.ICE_TERRAIN) {
            return Map.of("ok", false, "error", "not_water_cell");
        }
        List<String> changed = new ArrayList<>();
        String prefix = (regionId == null ? "" : regionId.trim()) + ":";
        for (Map.Entry<String, CellMark> e : cells.entrySet()) {
            if (e.getKey().startsWith(prefix)
                    && (e.getValue() == CellMark.WATER || e.getValue() == CellMark.ICE_TERRAIN)) {
                cells.put(e.getKey(), CellMark.ICE_TERRAIN);
                expireAt.put(e.getKey(), nowMs + 30_000L);
                changed.add(e.getKey());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", TERRAIN_REACTION);
        body.put("reaction", "FREEZE");
        body.put("zone", ICE_TERRAIN);
        body.put("durationMs", 30_000);
        body.put("walkSpeedMul", 0.7);
        body.put("slideSpeedMul", 1.3);
        body.put("canBearHeavyWeight", true);
        body.put("cells", changed);

        String curveId = regionId + ":freeze:" + gx + ":" + gz;
        Map<String, Object> curve = buildIceSpreadCurve(curveId, regionId, gx, gz, changed, nowMs);
        iceSpreadCurves.put(curveId, curve);
        icePhysicsLockAt.put(curveId, nowMs + ICE_PHYSICS_DELAY_MS);
        body.put("protocol", ICE_SPREAD_CRV);
        body.put("iceSpreadCurve", curve);
        body.put("physicsLockDelayMs", ICE_PHYSICS_DELAY_MS);
        body.put("physicsLockAtMs", nowMs + ICE_PHYSICS_DELAY_MS);
        body.put("clientHint", "冰面按曲线扩散渲染，2s 后锁定物理碰撞");
        body.put("surfaceFriction", 0.1f);
        body.put("brakeDeceleration", 0.5f);
        body.put("clientSlideSim", true);
        body.put("serverSyncIntervalMs", 300);
        return body;
    }

    private Map<String, Object> buildIceSpreadCurve(
            String curveId, String regionId, int seedGx, int seedGz,
            List<String> cells, long nowMs) {
        List<Map<String, Object>> controlPoints = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            String[] parts = cells.get(i).split(":");
            int gx = parts.length > 1 ? Integer.parseInt(parts[parts.length - 2]) : seedGx + i;
            int gz = parts.length > 0 ? Integer.parseInt(parts[parts.length - 1]) : seedGz + i;
            float t = cells.size() <= 1 ? 0f : (float) i / (cells.size() - 1);
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("gx", gx);
            pt.put("gz", gz);
            pt.put("t", Math.round(t * 100f) / 100f);
            pt.put("radiusM", 2f + t * 4f);
            controlPoints.add(pt);
        }
        Map<String, Object> curve = new LinkedHashMap<>();
        curve.put("curveId", curveId);
        curve.put("regionId", regionId);
        curve.put("waveform", "BEZIER_RIPPLE");
        curve.put("controlPoints", controlPoints);
        curve.put("spreadDurationMs", ICE_PHYSICS_DELAY_MS);
        curve.put("startedAtMs", nowMs);
        return curve;
    }

    public Map<String, Object> icePhysicsStatus(String curveId, long nowMs) {
        Long lockAt = icePhysicsLockAt.get(curveId);
        Map<String, Object> curve = iceSpreadCurves.get(curveId);
        if (curve == null) {
            return Map.of("ok", false, "error", "curve_not_found");
        }
        boolean locked = lockAt != null && nowMs >= lockAt;
        Map<String, Object> body = new LinkedHashMap<>(curve);
        body.put("ok", true);
        body.put("physicsLocked", locked);
        body.put("physicsLockAtMs", lockAt);
        body.put("canRunOnIce", !locked);
        return body;
    }

    public Map<String, Object> enterZone(String regionId, int gx, int gz, long nowMs) {
        String key = cellKey(regionId, gx, gz);
        Long exp = expireAt.get(key);
        if (exp != null && nowMs > exp) {
            cells.put(key, CellMark.WATER);
            expireAt.remove(key);
        }
        CellMark mark = cells.getOrDefault(key, CellMark.PLAIN);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("cell", key);
        body.put("mark", mark.name());
        if (mark == CellMark.ELECTRO_CHARGED_ZONE) {
            body.put("dot", "ELECTRO_CONDUCT");
            body.put("chainRadiusM", 5);
        }
        if (mark == CellMark.ICE_TERRAIN) {
            body.put("walkSpeedMul", 0.7);
            body.put("slideSpeedMul", 1.3);
            body.put("surfaceFriction", 0.1f);
            body.put("brakeDeceleration", 0.5f);
        }
        return body;
    }

    public CellMark markOf(String regionId, int gx, int gz) {
        return cells.getOrDefault(cellKey(regionId, gx, gz), CellMark.PLAIN);
    }
}
