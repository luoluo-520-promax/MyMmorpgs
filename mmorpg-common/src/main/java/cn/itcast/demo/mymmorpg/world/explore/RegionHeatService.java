package cn.itcast.demo.mymmorpg.world.explore;

import cn.itcast.demo.mymmorpg.world.content.ProceduralPlacementService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 探索熵值系统：统计 grid_cell 过去 1 小时访问热度，低热度区域触发微型事件注入。
 */
@Service
public class RegionHeatService {

    public static final long HEAT_WINDOW_MS = 3_600_000L;
    public static final int LOW_HEAT_THRESHOLD = 3;

    public record VisitEvent(long playerId, int gridX, int gridZ, long atMs) {
    }

    public record MicroEvent(
            String eventId,
            String regionId,
            int gridX,
            int gridZ,
            String kind,
            String description) {
    }

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<VisitEvent>> visitsByCell =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<MicroEvent>> injectedEvents =
            new ConcurrentHashMap<>();
    private final ProceduralPlacementService placement;
    private long seq = 1;

    public RegionHeatService() {
        this(new ProceduralPlacementService());
    }

    public RegionHeatService(ProceduralPlacementService placement) {
        this.placement = placement;
    }

    public Map<String, Object> recordVisit(
            String regionId, int gridX, int gridZ, long playerId, long nowMs) {
        String key = cellKey(regionId, gridX, gridZ);
        visitsByCell.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                .add(new VisitEvent(playerId, gridX, gridZ, nowMs));
        purgeExpired(key, nowMs);
        int heat = visitCount(key, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("gridX", gridX);
        body.put("gridZ", gridZ);
        body.put("playerVisitCount", heat);
        body.put("lowHeat", heat < LOW_HEAT_THRESHOLD);
        if (heat < LOW_HEAT_THRESHOLD) {
            Map<String, Object> injected = tryInjectMicroEvent(regionId, gridX, gridZ, nowMs);
            body.put("microEvent", injected);
        }
        return body;
    }

    public Map<String, Object> heatStatus(String regionId, int gridX, int gridZ, long nowMs) {
        String key = cellKey(regionId, gridX, gridZ);
        purgeExpired(key, nowMs);
        int heat = visitCount(key, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("gridX", gridX);
        body.put("gridZ", gridZ);
        body.put("playerVisitCount", heat);
        body.put("entropy", heat < LOW_HEAT_THRESHOLD ? "HIGH" : "LOW");
        body.put("windowMs", HEAT_WINDOW_MS);
        return body;
    }

    public List<Map<String, Object>> listInjectedEvents(String regionId) {
        List<MicroEvent> events = injectedEvents.getOrDefault(regionId, List.of());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MicroEvent e : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventId", e.eventId());
            row.put("gridX", e.gridX());
            row.put("gridZ", e.gridZ());
            row.put("kind", e.kind());
            row.put("description", e.description());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> tryInjectMicroEvent(String regionId, int gridX, int gridZ, long nowMs) {
        String kind = (gridX + gridZ) % 2 == 0 ? "ROCKFALL_BLOCK" : "CAVE_REVEAL";
        String desc = "ROCKFALL_BLOCK".equals(kind)
                ? "落石堵塞旧路，需绕行"
                : "岩壁裂开露出新洞口";
        MicroEvent event = new MicroEvent(
                regionId + "-evt-" + (seq++), regionId, gridX, gridZ, kind, desc);
        injectedEvents.computeIfAbsent(regionId, id -> new CopyOnWriteArrayList<>()).add(event);

        float baseX = gridX * 50f + 25f;
        float baseZ = gridZ * 50f + 25f;
        List<ProceduralPlacementService.TerrainSample> samples = List.of(
                new ProceduralPlacementService.TerrainSample(baseX, baseZ, 8f, 30f),
                new ProceduralPlacementService.TerrainSample(baseX + 10f, baseZ + 5f, 12f, 40f));
        Map<String, Object> placed = placement.fillGatherables(regionId, samples, 2, 0.5, 0.25);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("eventId", event.eventId());
        body.put("kind", kind);
        body.put("description", desc);
        body.put("placement", placed);
        return body;
    }

    private void purgeExpired(String key, long nowMs) {
        CopyOnWriteArrayList<VisitEvent> list = visitsByCell.get(key);
        if (list == null) {
            return;
        }
        list.removeIf(v -> nowMs - v.atMs() > HEAT_WINDOW_MS);
    }

    private int visitCount(String key, long nowMs) {
        purgeExpired(key, nowMs);
        CopyOnWriteArrayList<VisitEvent> list = visitsByCell.get(key);
        return list == null ? 0 : list.size();
    }

    private static String cellKey(String regionId, int gridX, int gridZ) {
        return (regionId == null ? "" : regionId.trim()) + ":" + gridX + ":" + gridZ;
    }
}
