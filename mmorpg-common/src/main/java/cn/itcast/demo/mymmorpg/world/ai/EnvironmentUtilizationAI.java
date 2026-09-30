package cn.itcast.demo.mymmorpg.world.ai;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境取用 AI：拾取可投掷物并造成百分比伤害+击退；独占锁定避免争抢。
 */
@Service
public class EnvironmentUtilizationAI {

    public static final String PICKUP_ENV_OBJECT = "PICKUP_ENV_OBJECT";

    private final ConcurrentHashMap<String, Long> envObjectHeld = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> envDefs = new ConcurrentHashMap<>();

    public void registerThrowable(String objectId, float x, float z, double damagePct) {
        envDefs.put(objectId == null ? "" : objectId.trim(), Map.of(
                "x", x, "z", z, "damagePct", damagePct <= 0 ? 0.15 : damagePct));
    }

    public Map<String, Object> tryPickup(long aiEntityId, String objectId, float aiX, float aiZ) {
        String id = objectId == null ? "" : objectId.trim();
        Map<String, Object> def = envDefs.get(id);
        if (def == null) {
            return Map.of("ok", false, "error", "object_not_found");
        }
        Long holder = envObjectHeld.get(id);
        if (holder != null && holder != aiEntityId) {
            return Map.of("ok", false, "error", "already_held", "holder", holder);
        }
        float ox = ((Number) def.get("x")).floatValue();
        float oz = ((Number) def.get("z")).floatValue();
        float dx = aiX - ox;
        float dz = aiZ - oz;
        if (dx * dx + dz * dz > 25f) {
            return Map.of("ok", false, "error", "too_far");
        }
        envObjectHeld.put(id, aiEntityId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", PICKUP_ENV_OBJECT);
        body.put("aiEntityId", aiEntityId);
        body.put("objectId", id);
        body.put("env_object_held", true);
        return body;
    }

    public Map<String, Object> throwAt(
            long aiEntityId, String objectId, long targetId, int targetMaxHp) {
        String id = objectId == null ? "" : objectId.trim();
        Long holder = envObjectHeld.get(id);
        if (holder == null || holder != aiEntityId) {
            return Map.of("ok", false, "error", "not_holding");
        }
        Map<String, Object> def = envDefs.getOrDefault(id, Map.of("damagePct", 0.15));
        double pct = ((Number) def.get("damagePct")).doubleValue();
        int dmg = Math.max(1, (int) Math.round(targetMaxHp * pct));
        envObjectHeld.remove(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("aiEntityId", aiEntityId);
        body.put("targetId", targetId);
        body.put("damage", dmg);
        body.put("damagePct", pct);
        body.put("knockback", true);
        body.put("objectId", id);
        return body;
    }

    public Long holderOf(String objectId) {
        return envObjectHeld.get(objectId == null ? "" : objectId.trim());
    }
}
