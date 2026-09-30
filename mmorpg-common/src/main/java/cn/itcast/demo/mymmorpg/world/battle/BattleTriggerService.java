package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 专武 special_effect_trigger 独立监听：命中后协同攻击等，与角色命座逻辑解耦。
 */
@Service
public class BattleTriggerService {

    public record WeaponRefineEffect(
            String weaponUid,
            int refineLv,
            String triggerEvent,
            String specialEffectTrigger,
            double damageRatio,
            int cooldownMs) {

        public WeaponRefineEffect {
            weaponUid = weaponUid == null ? "" : weaponUid.trim();
            refineLv = Math.max(1, Math.min(5, refineLv));
            triggerEvent = triggerEvent == null ? "ON_HIT" : triggerEvent.trim().toUpperCase();
            specialEffectTrigger = specialEffectTrigger == null
                    ? "COORDINATED_ATK" : specialEffectTrigger.trim();
            damageRatio = damageRatio <= 0 ? 0.15 * refineLv : damageRatio;
            cooldownMs = cooldownMs <= 0 ? 8_000 : cooldownMs;
        }
    }

    private final ConcurrentHashMap<String, WeaponRefineEffect> weaponEffects =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastFireMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Map<String, Object>>> firedLog =
            new ConcurrentHashMap<>();

    public void bindWeaponEffect(WeaponRefineEffect effect) {
        if (effect != null && !effect.weaponUid().isBlank()) {
            weaponEffects.put(effect.weaponUid(), effect);
        }
    }

    /**
     * 精炼升级：数值加伤由外部处理；此处只绑定/刷新 special_effect_trigger。
     */
    public Map<String, Object> refineWeapon(
            String weaponUid, int refineLv, String specialEffectTrigger, double damageRatio) {
        WeaponRefineEffect fx = new WeaponRefineEffect(
                weaponUid, refineLv, "ON_HIT", specialEffectTrigger, damageRatio, 8_000);
        bindWeaponEffect(fx);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("weaponUid", fx.weaponUid());
        body.put("refineLv", fx.refineLv());
        body.put("specialEffectTrigger", fx.specialEffectTrigger());
        body.put("damageRatio", fx.damageRatio());
        body.put("note", "refine_binds_trigger_not_only_damage");
        return body;
    }

    public Map<String, Object> onHit(
            String battleId, String weaponUid, long attackerId, long targetId,
            int baseDamage, long nowMs) {
        WeaponRefineEffect fx = weaponEffects.get(weaponUid == null ? "" : weaponUid.trim());
        if (fx == null) {
            return Map.of("ok", true, "triggered", false, "reason", "no_weapon_effect");
        }
        String cdKey = battleId + ":" + weaponUid;
        Long last = lastFireMs.get(cdKey);
        if (last != null && nowMs - last < fx.cooldownMs()) {
            return Map.of("ok", true, "triggered", false, "reason", "trigger_cd",
                    "remainMs", fx.cooldownMs() - (nowMs - last));
        }
        lastFireMs.put(cdKey, nowMs);
        int extra = Math.max(1, (int) Math.round(baseDamage * fx.damageRatio()));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", fx.specialEffectTrigger());
        event.put("triggerEvent", fx.triggerEvent());
        event.put("attackerId", attackerId);
        event.put("targetId", targetId);
        event.put("extraDamage", extra);
        event.put("refineLv", fx.refineLv());
        event.put("weaponUid", fx.weaponUid());
        event.put("decoupledFrom", "constellation");
        firedLog.computeIfAbsent(battleId == null ? "_" : battleId, id -> new ArrayList<>()).add(event);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("triggered", true);
        body.put("effect", event);
        return body;
    }

    public List<Map<String, Object>> recent(String battleId) {
        return List.copyOf(firedLog.getOrDefault(battleId, List.of()));
    }
}
