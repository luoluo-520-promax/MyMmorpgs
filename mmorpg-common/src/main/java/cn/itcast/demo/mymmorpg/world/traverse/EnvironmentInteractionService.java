package cn.itcast.demo.mymmorpg.world.traverse;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境交互：草木可燃、石头可投、属性技能与环境元素联动（探索解谜延伸）。
 */
@Service
public class EnvironmentInteractionService {

    public enum EnvElement {
        WOOD, VINE, STONE, WATER, ICE, OIL, GRASS
    }

    public enum SkillElement {
        FIRE, ELECTRO, CRYO, ANEMO, GEO, HYDRO, PHYSICAL
    }

    public record EnvObject(
            String objectId,
            EnvElement element,
            String state,
            int sceneId,
            float x, float y, float z) {
    }

    public enum PartyTalent {
        /** 厨师：采集食材概率双倍 */
        CHEF,
        /** 矿工：矿物击碎掉落增加 */
        MINER
    }

    private final ConcurrentHashMap<String, EnvObject> objects = new ConcurrentHashMap<>();
    /** playerId → 队伍携带的生态位天赋 */
    private final ConcurrentHashMap<Long, Set<PartyTalent>> partyTalents = new ConcurrentHashMap<>();

    public void register(EnvObject obj) {
        objects.put(obj.objectId(), obj);
    }

    public void setPartyTalents(long playerId, Set<PartyTalent> talents) {
        partyTalents.put(playerId, talents == null ? Set.of() : Set.copyOf(talents));
    }

    /**
     * 用技能元素作用于环境物体，产生点燃/结冰/碎石等结果。
     */
    public Map<String, Object> interact(String objectId, SkillElement skill, long playerId) {
        EnvObject obj = objects.get(objectId);
        if (obj == null) {
            return Map.of("ok", false, "error", "object_not_found");
        }
        if (skill == null) {
            skill = SkillElement.PHYSICAL;
        }
        String from = obj.state();
        String to = resolve(obj.element(), skill, from);
        String reason = reactionReason(obj.element(), skill);
        if (to.equals(from) && !"NONE".equals(reason)) {
            // 无状态变化但仍可能有副产物（如投掷）
        }
        EnvObject updated = new EnvObject(obj.objectId(), obj.element(), to,
                obj.sceneId(), obj.x(), obj.y(), obj.z());
        objects.put(objectId, updated);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("objectId", objectId);
        body.put("playerId", playerId);
        body.put("element", obj.element().name());
        body.put("skill", skill.name());
        body.put("from", from);
        body.put("to", to);
        body.put("reaction", reason);
        body.put("obstacleCleared", "CLEARED".equals(to) || "ASH".equals(to) || "SHATTERED".equals(to));
        if ("SHATTERED".equals(to) && obj.element() == EnvElement.STONE
                && partyTalents.getOrDefault(playerId, Set.of()).contains(PartyTalent.MINER)) {
            body.put("lootBonus", Map.of("oreExtra", 1, "talent", "MINER"));
        }
        return body;
    }

    /**
     * 采集结算：厨师天赋可双倍食材（不改战斗数值）。
     */
    public Map<String, Object> gatherWithTalent(
            long playerId, String itemId, int baseCount, boolean isFoodIngredient) {
        int count = Math.max(1, baseCount);
        boolean doubled = false;
        if (isFoodIngredient && partyTalents.getOrDefault(playerId, Set.of()).contains(PartyTalent.CHEF)) {
            count *= 2;
            doubled = true;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("itemId", itemId);
        body.put("count", count);
        body.put("doubled", doubled);
        body.put("talent", doubled ? "CHEF" : "NONE");
        return body;
    }

    public Map<String, Object> throwStone(String stoneObjectId, float targetX, float targetZ, long playerId) {
        EnvObject obj = objects.get(stoneObjectId);
        if (obj == null || obj.element() != EnvElement.STONE) {
            return Map.of("ok", false, "error", "not_a_stone");
        }
        objects.put(stoneObjectId, new EnvObject(obj.objectId(), obj.element(), "THROWN",
                obj.sceneId(), targetX, obj.y(), targetZ));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("objectId", stoneObjectId);
        body.put("playerId", playerId);
        body.put("landedX", targetX);
        body.put("landedZ", targetZ);
        body.put("reaction", "THROW");
        return body;
    }

    public EnvObject get(String objectId) {
        return objects.get(objectId);
    }

    private static String resolve(EnvElement env, SkillElement skill, String from) {
        if ("CLEARED".equals(from) || "ASH".equals(from) || "SHATTERED".equals(from)) {
            return from;
        }
        return switch (env) {
            case WOOD, VINE, GRASS, OIL -> skill == SkillElement.FIRE ? "ASH" : from;
            case ICE -> skill == SkillElement.FIRE ? "WATER_PUDDLE"
                    : skill == SkillElement.GEO ? "SHATTERED" : from;
            case WATER -> skill == SkillElement.CRYO ? "FROZEN"
                    : skill == SkillElement.ELECTRO ? "CONDUCTED" : from;
            case STONE -> skill == SkillElement.GEO || skill == SkillElement.PHYSICAL
                    ? "SHATTERED" : from;
        };
    }

    private static String reactionReason(EnvElement env, SkillElement skill) {
        if ((env == EnvElement.WOOD || env == EnvElement.VINE || env == EnvElement.GRASS
                || env == EnvElement.OIL) && skill == SkillElement.FIRE) {
            return "BURN";
        }
        if (env == EnvElement.VINE && skill == SkillElement.FIRE) {
            return "BURN_VINE_BARRIER";
        }
        if (env == EnvElement.ICE && skill == SkillElement.FIRE) {
            return "MELT";
        }
        if (env == EnvElement.WATER && skill == SkillElement.CRYO) {
            return "FREEZE";
        }
        if (env == EnvElement.STONE) {
            return "BREAK";
        }
        return "NONE";
    }
}
