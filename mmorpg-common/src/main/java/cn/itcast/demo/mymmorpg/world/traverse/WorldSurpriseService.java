package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 惊喜时刻：非指向性隐藏事件 / 彩蛋（瀑布后洞穴、特定动作触发小游戏）。
 */
@Service
public class WorldSurpriseService {

    public enum TriggerType {
        PROXIMITY, ACTION_EMOTE, ACTION_ATTACK, TIME_OF_DAY
    }

    public record SurpriseDef(
            String surpriseId,
            String title,
            TriggerType trigger,
            String triggerPayload,
            float x, float y, float z,
            float radius,
            String rewardItemId,
            int rewardCount) {

        public SurpriseDef {
            trigger = trigger == null ? TriggerType.PROXIMITY : trigger;
            radius = radius <= 0f ? 5f : radius;
            rewardCount = Math.max(1, rewardCount);
        }
    }

    private final ConcurrentHashMap<String, SurpriseDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> found = new ConcurrentHashMap<>();

    public void register(SurpriseDef def) {
        defs.put(def.surpriseId(), def);
    }

    public Map<String, Object> tryDiscover(
            long playerId, float px, float py, float pz,
            String action, int hourOfDay, long nowMs) {
        for (SurpriseDef d : defs.values()) {
            if (found.getOrDefault(playerId, Set.of()).contains(d.surpriseId())) {
                continue;
            }
            if (!matches(d, px, py, pz, action, hourOfDay)) {
                continue;
            }
            found.computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet()).add(d.surpriseId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("discovered", true);
            body.put("surpriseId", d.surpriseId());
            body.put("title", d.title());
            body.put("grantPlans", List.of(Map.of(
                    "itemId", d.rewardItemId() == null ? "primogem" : d.rewardItemId(),
                    "count", d.rewardCount())));
            body.put("settledAtMs", nowMs);
            body.put("idempotencyKey", "surprise:" + playerId + ":" + d.surpriseId());
            return body;
        }
        return Map.of("ok", true, "discovered", false);
    }

    public Map<String, Object> progress(long playerId) {
        return Map.of(
                "ok", true,
                "playerId", playerId,
                "foundCount", found.getOrDefault(playerId, Set.of()).size(),
                "total", defs.size(),
                "found", List.copyOf(found.getOrDefault(playerId, Set.of())));
    }

    private static boolean matches(
            SurpriseDef d, float px, float py, float pz, String action, int hourOfDay) {
        float dx = px - d.x();
        float dy = py - d.y();
        float dz = pz - d.z();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > d.radius()) {
            return false;
        }
        return switch (d.trigger()) {
            case PROXIMITY -> true;
            case ACTION_EMOTE, ACTION_ATTACK ->
                    action != null && action.equalsIgnoreCase(d.triggerPayload());
            case TIME_OF_DAY -> {
                try {
                    int need = Integer.parseInt(d.triggerPayload());
                    yield hourOfDay == need;
                } catch (Exception e) {
                    yield false;
                }
            }
        };
    }
}
