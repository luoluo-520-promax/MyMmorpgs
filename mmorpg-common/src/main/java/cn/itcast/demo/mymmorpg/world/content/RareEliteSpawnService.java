package cn.itcast.demo.mymmorpg.world.content;

import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 动态稀有精英：格子击杀达到阈值触发 GRID_INFESTATION，刷出金光精英并 AOI 广播。
 */
@Service
public class RareEliteSpawnService {

    public static final long WINDOW_MS = 15 * 60_000L;
    public static final int KILL_THRESHOLD = 30;
    public static final float BROADCAST_RADIUS_M = 500f;
    public static final String EVENT_RARE_ELITE_APPEAR = "RARE_ELITE_APPEAR";
    public static final String STATE_GRID_INFESTATION = "GRID_INFESTATION";

    public record KillTick(long atMs, int count) {
    }

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<KillTick>> killWindow =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> activeElites =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> gridState = new ConcurrentHashMap<>();
    private final ProceduralPlacementService placement;

    public RareEliteSpawnService() {
        this(new ProceduralPlacementService());
    }

    public RareEliteSpawnService(ProceduralPlacementService placement) {
        this.placement = placement == null ? new ProceduralPlacementService() : placement;
    }

    public Map<String, Object> recordKill(String gridCell, long nowMs) {
        if (gridCell == null || gridCell.isBlank()) {
            return Map.of("ok", false, "error", "grid_cell_required");
        }
        String cell = gridCell.trim();
        CopyOnWriteArrayList<KillTick> ticks =
                killWindow.computeIfAbsent(cell, id -> new CopyOnWriteArrayList<>());
        ticks.add(new KillTick(nowMs, 1));
        ticks.removeIf(t -> nowMs - t.atMs() > WINDOW_MS);
        int kills = ticks.stream().mapToInt(KillTick::count).sum();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("gridCell", cell);
        body.put("killsInWindow", kills);
        body.put("threshold", KILL_THRESHOLD);
        if (kills >= KILL_THRESHOLD && !STATE_GRID_INFESTATION.equals(gridState.get(cell))) {
            body.putAll(spawnRareElite(cell, nowMs));
        }
        return body;
    }

    public Map<String, Object> spawnRareElite(String gridCell, long nowMs) {
        String cell = gridCell.trim();
        gridState.put(cell, STATE_GRID_INFESTATION);
        String eliteId = "elite-gold-" + UUID.randomUUID();
        float[] xz = parseCellCenter(cell);
        placement.fillGatherables(
                "infest-" + cell,
                List.of(new ProceduralPlacementService.TerrainSample(xz[0], xz[1], 5f, 20f)),
                1, 0.1, 1.0);
        CollectibleService.DynamicLootTier loot =
                new CollectibleService.DynamicLootTier(6, 0.85f);
        Map<String, Object> elite = new LinkedHashMap<>();
        elite.put("eliteId", eliteId);
        elite.put("gridCell", cell);
        elite.put("x", xz[0]);
        elite.put("y", 2f);
        elite.put("z", xz[1]);
        elite.put("aura", "GOLDEN_GLOW");
        elite.put("state", STATE_GRID_INFESTATION);
        elite.put("spawnedAtMs", nowMs);
        elite.put("lootTier", loot.qualityBucket());
        elite.put("guaranteedDrop", Map.of(
                "itemId", "weapon_forge_mat",
                "count", 1,
                "source", "DynamicLootTier",
                "pity", true));
        activeElites.put(eliteId, elite);

        Map<String, Object> broadcast = new LinkedHashMap<>();
        broadcast.put("event", EVENT_RARE_ELITE_APPEAR);
        broadcast.put("radiusM", BROADCAST_RADIUS_M);
        broadcast.put("mapPin", "RED_ALERT");
        broadcast.put("eliteId", eliteId);
        broadcast.put("gridCell", cell);
        broadcast.put("x", xz[0]);
        broadcast.put("z", xz[1]);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("spawned", true);
        body.put("infestation", true);
        body.put("elite", elite);
        body.put("aoiBroadcast", broadcast);
        return body;
    }

    public Map<String, Object> settleKill(String eliteId, long killerPlayerId) {
        Map<String, Object> elite = activeElites.remove(eliteId);
        if (elite == null) {
            return Map.of("ok", false, "error", "elite_not_found");
        }
        String cell = String.valueOf(elite.get("gridCell"));
        gridState.remove(cell);
        killWindow.remove(cell);
        List<Map<String, Object>> grants = new ArrayList<>();
        grants.add(Map.of("itemId", "weapon_forge_mat", "count", 1, "pity", true));
        grants.add(Map.of("itemId", "gold", "count", 500 + ((Number) elite.getOrDefault("lootTier", 1)).intValue() * 200));
        return Map.of(
                "ok", true,
                "eliteId", eliteId,
                "killerPlayerId", killerPlayerId,
                "grantPlans", grants,
                "idempotencyKey", "rare-elite:" + eliteId);
    }

    public String gridStateOf(String gridCell) {
        return gridState.getOrDefault(gridCell, "");
    }

    private static float[] parseCellCenter(String cell) {
        // 约定 grid_cell = "cx_cz" 或任意字符串哈希到坐标
        try {
            String[] p = cell.split("[_:,]");
            if (p.length >= 2) {
                return new float[]{Float.parseFloat(p[0]) * 50f + 25f, Float.parseFloat(p[1]) * 50f + 25f};
            }
        } catch (Exception ignored) {
            // fall through
        }
        int h = cell.hashCode();
        return new float[]{(h & 0xFF) * 10f, ((h >> 8) & 0xFF) * 10f};
    }
}
