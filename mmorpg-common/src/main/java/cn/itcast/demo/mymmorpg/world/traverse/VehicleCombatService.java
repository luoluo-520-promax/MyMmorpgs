package cn.itcast.demo.mymmorpg.world.traverse;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 载具战斗：骑乘态技能槽、独立韧性条、损毁弹射滑翔。
 */
@Service
public class VehicleCombatService {

    public static final String EVENT_EJECT_GLIDE = "EJECT_GLIDE";

    public record VehicleCombatDef(
            String vehicleId,
            List<String> skillSlots,
            float poiseMax,
            int massRating) {
        public VehicleCombatDef {
            vehicleId = vehicleId == null ? "" : vehicleId.trim();
            skillSlots = skillSlots == null ? List.of() : List.copyOf(skillSlots);
            poiseMax = poiseMax <= 0 ? 100f : poiseMax;
            massRating = Math.max(1, Math.min(10, massRating <= 0 ? 6 : massRating));
        }

        public VehicleCombatDef(String vehicleId, List<String> skillSlots, float poiseMax) {
            this(vehicleId, skillSlots, poiseMax, 6);
        }
    }

    private final ConcurrentHashMap<String, VehicleCombatDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Float> poise = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> boarded = new ConcurrentHashMap<>();

    public void register(VehicleCombatDef def) {
        if (def != null && !def.vehicleId().isBlank()) {
            defs.put(def.vehicleId(), def);
            poise.put(def.vehicleId(), def.poiseMax());
        }
    }

    public Map<String, Object> board(long playerId, String vehicleId) {
        VehicleCombatDef def = defs.get(vehicleId == null ? "" : vehicleId.trim());
        if (def == null) {
            return Map.of("ok", false, "error", "vehicle_not_found");
        }
        boarded.put(playerId, def.vehicleId());
        return Map.of("ok", true, "playerId", playerId, "vehicleId", def.vehicleId(),
                "poise", poise.get(def.vehicleId()), "skillSlots", def.skillSlots());
    }

    public Map<String, Object> castSkill(long playerId, String skillId) {
        String vid = boarded.get(playerId);
        if (vid == null) {
            return Map.of("ok", false, "error", "not_boarding");
        }
        VehicleCombatDef def = defs.get(vid);
        if (def == null || !def.skillSlots().contains(skillId)) {
            return Map.of("ok", false, "error", "skill_not_in_vehicle_slots");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("msgId", MessageId.VEHICLE_CAST_SKILL_CS_REQ);
        body.put("event", "VehicleCastSkillReq");
        body.put("playerId", playerId);
        body.put("vehicleId", vid);
        body.put("skillId", skillId);
        return body;
    }

    public Map<String, Object> applyPoiseDamage(
            long playerId, float damage, float horizontalSpeed) {
        return applyPoiseDamage(playerId, damage, horizontalSpeed, 0.15);
    }

    /**
     * 载具承伤；韧性归零 → 弹射起飞；MassRating 影响击退距离。
     */
    public Map<String, Object> applyPoiseDamage(
            long playerId, float damage, float horizontalSpeed, double groundFriction) {
        String vid = boarded.get(playerId);
        if (vid == null) {
            return Map.of("ok", false, "error", "not_boarding");
        }
        VehicleCombatDef def = defs.get(vid);
        float cur = poise.merge(vid, -Math.abs(damage), Float::sum);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("vehicleId", vid);
        body.put("poise", cur);
        body.put("poiseMax", def == null ? 100f : def.poiseMax());
        int mass = def == null ? 6 : def.massRating();
        body.put("massRating", mass);
        double ejectDist = (damage / mass) * (1.0 - Math.max(0, Math.min(0.95, groundFriction)));
        body.put("knockBackDistance", Math.round(ejectDist * 1000d) / 1000d);
        if (cur <= 0) {
            boarded.remove(playerId);
            poise.put(vid, def == null ? 100f : def.poiseMax());
            body.put("destroyed", true);
            body.put("event", EVENT_EJECT_GLIDE);
            body.put("inheritedHorizontalSpeed", horizontalSpeed);
            body.put("movementType", "GLIDE");
        } else {
            body.put("destroyed", false);
        }
        return body;
    }
}
