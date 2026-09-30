package cn.itcast.demo.mymmorpg.world.feel;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微观地形细节 Redis 语义热数据：脚印 30s TTL、投射物/碎石残留 5min TTL。
 * 键：{@code terrain:footprint:{zone}} / {@code terrain:debris:{zone}}。
 */
@Service
public class TerrainDetailTracker {

    public static final long FOOTPRINT_TTL_MS = 30_000L;
    public static final long DEBRIS_TTL_MS = 300_000L;

    public record FootprintEntry(
            String id, float x, float y, float z, float yaw, String surfaceType,
            long playerId, long createdAtMs, long expireAtMs) {
    }

    public record DebrisEntry(
            String id, String debrisType, float x, float y, float z,
            float normalX, float normalY, float normalZ, float yaw,
            long ownerId, long createdAtMs, long expireAtMs) {
    }

    private final ConcurrentHashMap<String, List<FootprintEntry>> footprints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DebrisEntry>> debris = new ConcurrentHashMap<>();

    public static String footprintKey(String zone) {
        return "terrain:footprint:" + norm(zone);
    }

    public static String debrisKey(String zone) {
        return "terrain:debris:" + norm(zone);
    }

    public FootprintEntry addFootprint(
            String zone, long playerId, float x, float y, float z,
            float yaw, String surfaceType, long nowMs) {
        purgeExpired(zone, nowMs);
        String id = "fp:" + playerId + ":" + nowMs;
        FootprintEntry entry = new FootprintEntry(
                id, x, y, z, yaw, surfaceType == null ? "PLAIN" : surfaceType,
                playerId, nowMs, nowMs + FOOTPRINT_TTL_MS);
        footprints.computeIfAbsent(norm(zone), k -> new ArrayList<>()).add(entry);
        return entry;
    }

    public DebrisEntry addDebris(
            String zone, String debrisType, long ownerId,
            float x, float y, float z,
            float nx, float ny, float nz, float yaw, long nowMs) {
        purgeExpired(zone, nowMs);
        String id = "db:" + ownerId + ":" + nowMs;
        DebrisEntry entry = new DebrisEntry(
                id, debrisType == null ? "ARROW" : debrisType,
                x, y, z, nx, ny, nz, yaw, ownerId, nowMs, nowMs + DEBRIS_TTL_MS);
        debris.computeIfAbsent(norm(zone), k -> new ArrayList<>()).add(entry);
        return entry;
    }

    public List<FootprintEntry> listFootprints(String zone, long nowMs) {
        purgeExpired(zone, nowMs);
        return List.copyOf(footprints.getOrDefault(norm(zone), List.of()));
    }

    public List<DebrisEntry> listDebris(String zone, long nowMs) {
        purgeExpired(zone, nowMs);
        return List.copyOf(debris.getOrDefault(norm(zone), List.of()));
    }

    public Map<String, Object> snapshotZone(String zone, long nowMs) {
        purgeExpired(zone, nowMs);
        String z = norm(zone);
        List<Map<String, Object>> fp = new ArrayList<>();
        for (FootprintEntry e : listFootprints(z, nowMs)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.id());
            row.put("x", e.x());
            row.put("y", e.y());
            row.put("z", e.z());
            row.put("yaw", e.yaw());
            row.put("surfaceType", e.surfaceType());
            row.put("playerId", e.playerId());
            row.put("expireAtMs", e.expireAtMs());
            fp.add(row);
        }
        List<Map<String, Object>> db = new ArrayList<>();
        for (DebrisEntry e : listDebris(z, nowMs)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.id());
            row.put("debrisType", e.debrisType());
            row.put("x", e.x());
            row.put("y", e.y());
            row.put("z", e.z());
            row.put("normalX", e.normalX());
            row.put("normalY", e.normalY());
            row.put("normalZ", e.normalZ());
            row.put("yaw", e.yaw());
            row.put("ownerId", e.ownerId());
            row.put("expireAtMs", e.expireAtMs());
            db.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("zone", z);
        body.put("footprintKey", footprintKey(z));
        body.put("debrisKey", debrisKey(z));
        body.put("footprints", fp);
        body.put("debris", db);
        body.put("footprintTtlMs", FOOTPRINT_TTL_MS);
        body.put("debrisTtlMs", DEBRIS_TTL_MS);
        return body;
    }

    private void purgeExpired(String zone, long nowMs) {
        String z = norm(zone);
        List<FootprintEntry> fp = footprints.get(z);
        if (fp != null) {
            fp.removeIf(e -> e.expireAtMs() <= nowMs);
        }
        List<DebrisEntry> db = debris.get(z);
        if (db != null) {
            db.removeIf(e -> e.expireAtMs() <= nowMs);
        }
    }

    public void clearZone(String zone) {
        String z = norm(zone);
        footprints.remove(z);
        debris.remove(z);
    }

    private static String norm(String zone) {
        return zone == null || zone.isBlank() ? "default" : zone.trim();
    }
}
