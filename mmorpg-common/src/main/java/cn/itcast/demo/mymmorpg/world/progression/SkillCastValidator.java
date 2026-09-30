package cn.itcast.demo.mymmorpg.world.progression;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能施法校验：当 constellation_lv &gt;= 1 时读 constellation_buff_override，
 * 允许改冷却、投射物数量或追加一次伤害判定（如 Evil Warding）。
 */
@Service
public class SkillCastValidator {

    public record SkillBase(
            String skillId,
            int cooldownMs,
            int projectileCount,
            int baseHits,
            int mpCost) {

        public SkillBase {
            skillId = skillId == null ? "" : skillId.trim();
            cooldownMs = Math.max(0, cooldownMs);
            projectileCount = Math.max(1, projectileCount);
            baseHits = Math.max(1, baseHits);
            mpCost = Math.max(0, mpCost);
        }
    }

    private final ConcurrentHashMap<String, SkillBase> skills = new ConcurrentHashMap<>();
    private final ConstellationService constellation;

    public SkillCastValidator() {
        this(new ConstellationService());
    }

    public SkillCastValidator(ConstellationService constellation) {
        this.constellation = constellation == null ? new ConstellationService() : constellation;
    }

    public void registerSkill(SkillBase skill) {
        if (skill != null && !skill.skillId().isBlank()) {
            skills.put(skill.skillId(), skill);
        }
    }

    /**
     * 解析有效技能参数：命座覆盖优先于原始技能表。
     */
    public Map<String, Object> resolveCast(
            long playerId, String characterId, String skillId, long nowMs) {
        SkillBase base = skills.get(skillId == null ? "" : skillId.trim());
        if (base == null) {
            return Map.of("ok", false, "error", "skill_not_found");
        }
        int lv = constellation.levelOf(playerId, characterId);
        int cooldownMs = base.cooldownMs();
        int projectiles = base.projectileCount();
        int hits = base.baseHits();
        String effectTag = "";
        boolean fromOverride = false;

        if (lv >= 1) {
            List<Map<String, Object>> ovs = constellation.resolveOverrides(characterId, lv);
            for (Map<String, Object> ov : ovs) {
                if (!base.skillId().equals(String.valueOf(ov.get("skillId")))) {
                    continue;
                }
                fromOverride = true;
                if (ov.get("cooldownMs") instanceof Number n) {
                    cooldownMs = n.intValue();
                }
                if (ov.get("projectileCount") instanceof Number n) {
                    projectiles = Math.max(1, n.intValue());
                }
                if (Boolean.TRUE.equals(ov.get("extraDamageHit"))) {
                    hits = base.baseHits() + 1;
                }
                effectTag = String.valueOf(ov.getOrDefault("effectTag", ""));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("skillId", base.skillId());
        body.put("playerId", playerId);
        body.put("characterId", characterId);
        body.put("constellationLv", lv);
        body.put("source", fromOverride ? "constellation_buff_override" : "skill_table");
        body.put("cooldownMs", cooldownMs);
        body.put("projectileCount", projectiles);
        body.put("hitCount", hits);
        body.put("mpCost", base.mpCost());
        body.put("effectTag", effectTag);
        body.put("castAtMs", nowMs);
        body.put("cdEndMs", nowMs + cooldownMs);
        return body;
    }
}
