package cn.itcast.demo.mymmorpg.world.sideplay;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 大世界休闲玩法：钓鱼 / 赛跑 / 音游，嵌入特定区域。
 */
@Service
public class LeisureActivityService {

    public enum LeisureType {
        FISHING, RACING, RHYTHM
    }

    public record Zone(
            String zoneId,
            LeisureType type,
            int sceneId,
            float x, float z,
            float radius) {
    }

    private final ConcurrentHashMap<String, Zone> zones = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> highScores = new ConcurrentHashMap<>();

    public void registerZone(Zone zone) {
        zones.put(zone.zoneId(), zone);
    }

    public Map<String, Object> play(long playerId, String zoneId, float px, float pz, int inputScore) {
        Zone zone = zones.get(zoneId);
        if (zone == null) {
            return Map.of("ok", false, "error", "zone_not_found");
        }
        float dx = px - zone.x();
        float dz = pz - zone.z();
        if (Math.sqrt(dx * dx + dz * dz) > zone.radius()) {
            return Map.of("ok", false, "error", "out_of_zone");
        }
        int score = switch (zone.type()) {
            case FISHING -> Math.max(1, inputScore) + ThreadLocalRandom.current().nextInt(0, 20);
            case RACING -> Math.max(0, 10_000 - Math.max(0, inputScore)); // inputScore=用时ms
            case RHYTHM -> Math.max(0, Math.min(1_000_000, inputScore));
        };
        int prev = highScores.getOrDefault(playerId, 0);
        boolean best = score > prev;
        if (best) {
            highScores.put(playerId, score);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("zoneId", zoneId);
        body.put("type", zone.type().name());
        body.put("score", score);
        body.put("personalBest", best);
        body.put("bestScore", highScores.getOrDefault(playerId, score));
        body.put("grantPlans", score >= 50
                ? java.util.List.of(Map.of("itemId", "leisure_coin", "count", Math.min(20, score / 50)))
                : java.util.List.of());
        return body;
    }

    public Map<String, Object> listZones() {
        return Map.of("ok", true, "zones", zones.values().stream().map(z -> Map.of(
                "zoneId", z.zoneId(),
                "type", z.type().name(),
                "sceneId", z.sceneId(),
                "x", z.x(),
                "z", z.z(),
                "radius", z.radius()
        )).toList());
    }
}
