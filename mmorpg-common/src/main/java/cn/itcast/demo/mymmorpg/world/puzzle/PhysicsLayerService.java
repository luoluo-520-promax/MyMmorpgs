package cn.itcast.demo.mymmorpg.world.puzzle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量物理状态层：水面冻结、草丛燃烧蔓延等，按 AOI 网格广播给客户端。
 * <p>
 * 扩展 WorldState 的 physics_layer，而非仅记录开关 Bitmap。
 */
@Service
public class PhysicsLayerService {

    public enum PhysicsKind {
        FIRE, FROZEN, WET, OIL, WIND, ASH
    }

    public record CellState(
            int worldId,
            int gridX,
            int gridZ,
            PhysicsKind kind,
            int intensity,
            long expireAtMs) {
    }

    private final ConcurrentHashMap<String, CellState> cells = new ConcurrentHashMap<>();
    private final ZoneLifecycleManager zoneLifecycle;
    private int gridSize = 16;

    public PhysicsLayerService() {
        this(new ZoneLifecycleManager());
    }

    public PhysicsLayerService(ZoneLifecycleManager zoneLifecycle) {
        this.zoneLifecycle = zoneLifecycle == null ? new ZoneLifecycleManager() : zoneLifecycle;
    }

    public void configure(int gridSize) {
        this.gridSize = Math.max(4, gridSize);
    }

    public Map<String, Object> apply(
            int worldId, float x, float z, PhysicsKind kind, int intensity, long ttlMs, long nowMs) {
        if (kind == null) {
            return Map.of("ok", false, "error", "kind_required");
        }
        int gx = toGrid(x);
        int gz = toGrid(z);
        String key = cellKey(worldId, gx, gz, kind);
        long expire = nowMs + Math.max(1_000L, ttlMs);
        CellState state = new CellState(worldId, gx, gz, kind, Math.max(1, intensity), expire);
        cells.put(key, state);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("cell", toView(state));
        body.put("spreadCandidates", List.of());
        // 火+草地：不仅变色，还注册持续伤害判定框（默认 3s / 0.5s）
        if (kind == PhysicsKind.FIRE) {
            int dmg = Math.max(5, intensity * 8);
            body.put("persistentZone", zoneLifecycle.spawnBurnZone(
                    worldId, x, z, gridSize * 0.6f, dmg, nowMs));
        }
        return body;
    }

    public ZoneLifecycleManager zones() {
        return zoneLifecycle;
    }

    /** Tick 持续效果区并对进入实体结算伤害。entityPositions: entityId → [x,z] */
    public Map<String, Object> tickZones(int worldId, Map<Long, float[]> entityPositions, long nowMs) {
        return zoneLifecycle.tick(worldId, entityPositions, nowMs);
    }

    /**
     * 火焰蔓延：邻格若为 GRASS/OIL 标记则扩散；此处用显式 neighborHints。
     */
    public Map<String, Object> tickSpread(int worldId, long nowMs, List<String> flammableHints) {
        purgeExpired(nowMs);
        List<Map<String, Object>> spread = new ArrayList<>();
        List<CellState> fires = cells.values().stream()
                .filter(c -> c.worldId() == worldId && c.kind() == PhysicsKind.FIRE)
                .filter(c -> c.expireAtMs() > nowMs)
                .toList();
        for (CellState fire : fires) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = fire.gridX() + d[0];
                int nz = fire.gridZ() + d[1];
                String hint = worldId + ":" + nx + ":" + nz;
                if (flammableHints != null && !flammableHints.contains(hint)) {
                    continue;
                }
                String key = cellKey(worldId, nx, nz, PhysicsKind.FIRE);
                if (cells.containsKey(key)) {
                    continue;
                }
                CellState next = new CellState(worldId, nx, nz, PhysicsKind.FIRE,
                        Math.max(1, fire.intensity() - 1), nowMs + 8_000L);
                cells.put(key, next);
                spread.add(toView(next));
            }
        }
        return Map.of("ok", true, "worldId", worldId, "spread", spread, "spreadCount", spread.size());
    }

    public Map<String, Object> freezeSurface(int worldId, float x, float z, long ttlMs, long nowMs) {
        return apply(worldId, x, z, PhysicsKind.FROZEN, 3, ttlMs, nowMs);
    }

    /** AOI 查询：返回玩家周边网格的物理状态，供客户端广播。 */
    public Map<String, Object> queryAoi(int worldId, float x, float z, int radiusCells, long nowMs) {
        purgeExpired(nowMs);
        int cx = toGrid(x);
        int cz = toGrid(z);
        int r = Math.max(1, radiusCells);
        List<Map<String, Object>> list = new ArrayList<>();
        for (CellState c : cells.values()) {
            if (c.worldId() != worldId || c.expireAtMs() <= nowMs) {
                continue;
            }
            if (Math.abs(c.gridX() - cx) <= r && Math.abs(c.gridZ() - cz) <= r) {
                list.add(toView(c));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("worldId", worldId);
        body.put("gridSize", gridSize);
        body.put("center", Map.of("gx", cx, "gz", cz));
        body.put("physicsLayer", list);
        body.put("count", list.size());
        return body;
    }

    public Map<String, Object> snapshot(int worldId, long nowMs) {
        purgeExpired(nowMs);
        List<Map<String, Object>> list = cells.values().stream()
                .filter(c -> c.worldId() == worldId)
                .map(this::toView)
                .toList();
        return Map.of("ok", true, "worldId", worldId, "physicsLayer", list, "count", list.size());
    }

    private void purgeExpired(long nowMs) {
        cells.entrySet().removeIf(e -> e.getValue().expireAtMs() <= nowMs);
    }

    private int toGrid(float v) {
        return (int) Math.floor(v / (float) gridSize);
    }

    private static String cellKey(int worldId, int gx, int gz, PhysicsKind kind) {
        return worldId + ":" + gx + ":" + gz + ":" + kind.name();
    }

    private Map<String, Object> toView(CellState c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("worldId", c.worldId());
        m.put("gridX", c.gridX());
        m.put("gridZ", c.gridZ());
        m.put("kind", c.kind().name());
        m.put("intensity", c.intensity());
        m.put("expireAtMs", c.expireAtMs());
        return m;
    }
}
