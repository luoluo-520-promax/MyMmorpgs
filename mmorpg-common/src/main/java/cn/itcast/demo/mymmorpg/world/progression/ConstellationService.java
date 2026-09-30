package cn.itcast.demo.mymmorpg.world.progression;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命座机制挂载：constellation_lv 解锁技能机制覆盖（冷却/投射物/追加伤害判定），而非纯数值。
 */
@Service
public class ConstellationService {

    public static final int MSG_CONSTELLATION_UNLOCK_CS_REQ = 1210;
    public static final int MSG_CONSTELLATION_UNLOCK_SC_RSP = 1211;
    public static final int MSG_ATTRIBUTE_RECALC_SC_NOTIFY = 1212;

    public record ConstellationBuffOverride(
            String characterId,
            int requiredLv,
            String skillId,
            Integer cooldownMsOverride,
            Integer projectileCountOverride,
            boolean extraDamageHit,
            String effectTag) {

        public ConstellationBuffOverride {
            characterId = characterId == null ? "" : characterId.trim();
            skillId = skillId == null ? "" : skillId.trim();
            requiredLv = Math.max(1, requiredLv);
            effectTag = effectTag == null ? "" : effectTag;
        }
    }

    private final ConcurrentHashMap<String, List<ConstellationBuffOverride>> overrides =
            new ConcurrentHashMap<>();
    /** playerId:characterId → level */
    private final ConcurrentHashMap<String, Integer> levels = new ConcurrentHashMap<>();
    /** playerId → 命座材料库存（演示；生产由 BagService 扣减） */
    private final ConcurrentHashMap<Long, Integer> constellationMats = new ConcurrentHashMap<>();

    public void registerOverride(ConstellationBuffOverride ov) {
        if (ov == null || ov.characterId().isBlank() || ov.skillId().isBlank()) {
            return;
        }
        overrides.computeIfAbsent(ov.characterId(), id -> new ArrayList<>()).add(ov);
    }

    public void creditMaterial(long playerId, int amount) {
        constellationMats.merge(playerId, Math.max(0, amount), Integer::sum);
    }

    public int materialCount(long playerId) {
        return constellationMats.getOrDefault(playerId, 0);
    }

    public int levelOf(long playerId, String characterId) {
        return levels.getOrDefault(key(playerId, characterId), 0);
    }

    /**
     * MSG_CONSTELLATION_UNLOCK：扣材料升一级，广播 AttributeRecalc。
     */
    public Map<String, Object> unlock(long playerId, String characterId, int matCost) {
        if (playerId <= 0 || characterId == null || characterId.isBlank()) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        int cost = Math.max(1, matCost);
        int have = constellationMats.getOrDefault(playerId, 0);
        if (have < cost) {
            return Map.of("ok", false, "error", "insufficient_constellation_mat",
                    "need", cost, "have", have, "msgId", MSG_CONSTELLATION_UNLOCK_SC_RSP);
        }
        constellationMats.put(playerId, have - cost);
        String k = key(playerId, characterId.trim());
        int next = levels.merge(k, 1, Integer::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("msgId", MSG_CONSTELLATION_UNLOCK_SC_RSP);
        body.put("playerId", playerId);
        body.put("characterId", characterId.trim());
        body.put("constellationLv", next);
        body.put("materialCost", cost);
        body.put("attributeRecalc", Map.of(
                "msgId", MSG_ATTRIBUTE_RECALC_SC_NOTIFY,
                "reason", "CONSTELLATION_UNLOCK",
                "characterId", characterId.trim(),
                "constellationLv", next));
        body.put("activeOverrides", resolveOverrides(characterId.trim(), next));
        return body;
    }

    public List<Map<String, Object>> resolveOverrides(String characterId, int constellationLv) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (constellationLv < 1) {
            return out;
        }
        for (ConstellationBuffOverride ov : overrides.getOrDefault(characterId, List.of())) {
            if (ov.requiredLv() > constellationLv) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skillId", ov.skillId());
            row.put("requiredLv", ov.requiredLv());
            if (ov.cooldownMsOverride() != null) {
                row.put("cooldownMs", ov.cooldownMsOverride());
            }
            if (ov.projectileCountOverride() != null) {
                row.put("projectileCount", ov.projectileCountOverride());
            }
            row.put("extraDamageHit", ov.extraDamageHit());
            row.put("effectTag", ov.effectTag());
            out.add(row);
        }
        return out;
    }

    private static String key(long playerId, String characterId) {
        return playerId + ":" + characterId;
    }
}
