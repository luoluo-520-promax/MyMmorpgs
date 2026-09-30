package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 韧性条 / 霸体：重击破韧、开大超级装甲、处决窗口；硬直必须服务端 HIT_CONFIRM。
 */
@Service
public class PoiseService {

    public static final String EVENT_EXECUTION_TRIGGER = "EXECUTION_TRIGGER";
    public static final String EVENT_HIT_CONFIRM = "HIT_CONFIRM";
    public static final long DEFENSE_ZERO_MS = 5_000L;

    public enum StaggerLevel {
        NONE(0), LIGHT(1), LAUNCH(2);

        private final int code;

        StaggerLevel(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    /** 韧性霸体：可被削韧；闪避无敌帧：仅闪避专属 */
    public enum ArmorLayer {
        NONE, POISE_ARMOR, DODGE_IFRAME
    }

    public record PoiseState(
            long entityId,
            double poiseMax,
            double poiseCurrent,
            double poiseRegenPerSec,
            boolean superArmor,
            long defenseZeroUntilMs,
            ArmorLayer armorLayer,
            long iframeUntilMs,
            double poiseBreakThreshold,
            long stiffnessRemainMs) {
    }

    private final ConcurrentHashMap<Long, PoiseState> states = new ConcurrentHashMap<>();

    public Map<String, Object> initEntity(long entityId, double poiseMax, double regenPerSec) {
        double max = poiseMax <= 0 ? 100 : poiseMax;
        PoiseState s = new PoiseState(entityId, max, max, Math.max(0, regenPerSec),
                false, 0, ArmorLayer.NONE, 0, max * 0.3, 0);
        states.put(entityId, s);
        return Map.of("ok", true, "entityId", entityId, "poiseMax", max, "poiseCurrent", max);
    }

    /** 进入硬直后摇（HIT_CONFIRM），可被高优先级取消打断。 */
    public Map<String, Object> enterStiffness(long entityId, int stiffnessMs, long nowMs) {
        PoiseState cur = states.get(entityId);
        if (cur == null) {
            initEntity(entityId, 100, 5);
            cur = states.get(entityId);
        }
        long remain = Math.max(1, stiffnessMs);
        PoiseState next = new PoiseState(
                cur.entityId(), cur.poiseMax(), cur.poiseCurrent(),
                cur.poiseRegenPerSec(), cur.superArmor(), cur.defenseZeroUntilMs(),
                cur.armorLayer(), cur.iframeUntilMs(), cur.poiseBreakThreshold(), remain);
        states.put(entityId, next);
        return Map.of("ok", true, "entityId", entityId, "event", EVENT_HIT_CONFIRM,
                "stiffnessRemainMs", remain);
    }

    /** 高优先级取消成功时重置硬直剩余帧。 */
    public Map<String, Object> resetStiffness(long entityId, CancelAction cancelAction) {
        PoiseState cur = states.get(entityId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_found");
        }
        PoiseState next = new PoiseState(
                cur.entityId(), cur.poiseMax(), cur.poiseCurrent(),
                cur.poiseRegenPerSec(), cur.superArmor(), cur.defenseZeroUntilMs(),
                cur.armorLayer(), cur.iframeUntilMs(), cur.poiseBreakThreshold(), 0);
        states.put(entityId, next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("entityId", entityId);
        body.put("stiffnessCleared", true);
        body.put("cancelAction", cancelAction == null ? CancelAction.NONE.name() : cancelAction.name());
        return body;
    }

    public long stiffnessRemainMs(long entityId) {
        PoiseState s = states.get(entityId);
        return s == null ? 0 : s.stiffnessRemainMs();
    }

    /** 闪避无敌帧：免疫伤害与打断，下发 poiseBreakThreshold 倒计时 UI */
    public Map<String, Object> enterDodgeIframe(long entityId, int iframeMs, long nowMs) {
        PoiseState cur = states.get(entityId);
        if (cur == null) {
            initEntity(entityId, 100, 5);
            cur = states.get(entityId);
        }
        long until = nowMs + Math.max(1, iframeMs);
        PoiseState next = new PoiseState(
                cur.entityId(), cur.poiseMax(), cur.poiseCurrent(),
                cur.poiseRegenPerSec(), false, cur.defenseZeroUntilMs(),
                ArmorLayer.DODGE_IFRAME, until, 0, cur.stiffnessRemainMs());
        states.put(entityId, next);
        return Map.of("ok", true, "entityId", entityId, "armorLayer", ArmorLayer.DODGE_IFRAME.name(),
                "iframeUntilMs", until, "poiseBreakThreshold", 0, "invincible", true);
    }

    /** 韧性霸体：可被削韧，下发破韧阈值倒计时 */
    public Map<String, Object> enterPoiseArmor(long entityId, double breakThreshold, long nowMs) {
        PoiseState cur = states.get(entityId);
        if (cur == null) {
            initEntity(entityId, 100, 5);
            cur = states.get(entityId);
        }
        PoiseState next = new PoiseState(
                cur.entityId(), cur.poiseMax(), cur.poiseCurrent(),
                cur.poiseRegenPerSec(), true, cur.defenseZeroUntilMs(),
                ArmorLayer.POISE_ARMOR, 0, breakThreshold, cur.stiffnessRemainMs());
        states.put(entityId, next);
        return Map.of("ok", true, "entityId", entityId, "armorLayer", ArmorLayer.POISE_ARMOR.name(),
                "poiseBreakThreshold", breakThreshold, "interruptImmune", false, "atMs", nowMs);
    }

    /** 开大：isSuperArmor=true，免疫打断，非无敌。 */
    public Map<String, Object> enterUltimate(long entityId, long nowMs) {
        PoiseState cur = states.get(entityId);
        if (cur == null) {
            initEntity(entityId, 100, 5);
            cur = states.get(entityId);
        }
        PoiseState next = new PoiseState(
                cur.entityId(), cur.poiseMax(), cur.poiseCurrent(),
                cur.poiseRegenPerSec(), true, cur.defenseZeroUntilMs(),
                ArmorLayer.NONE, cur.iframeUntilMs(), cur.poiseBreakThreshold(), cur.stiffnessRemainMs());
        states.put(entityId, next);
        return Map.of("ok", true, "entityId", entityId, "isSuperArmor", true,
                "ultimate", true, "interruptImmune", true, "invincible", false, "atMs", nowMs);
    }

    public Map<String, Object> leaveUltimate(long entityId) {
        PoiseState cur = states.get(entityId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_found");
        }
        states.put(entityId, new PoiseState(
                cur.entityId(), cur.poiseMax(), cur.poiseCurrent(),
                cur.poiseRegenPerSec(), false, cur.defenseZeroUntilMs(),
                ArmorLayer.NONE, cur.iframeUntilMs(), cur.poiseBreakThreshold(), cur.stiffnessRemainMs()));
        return Map.of("ok", true, "isSuperArmor", false);
    }

    /**
     * BattleActionHandler 钩子：应用 attack_poise_damage，返回 HIT_CONFIRM + stagger_level。
     */
    public Map<String, Object> applyPoiseDamage(
            long attackerId, long targetId, double attackPoiseDamage, long nowMs) {
        PoiseState cur = states.get(targetId);
        if (cur == null) {
            initEntity(targetId, 100, 5);
            cur = states.get(targetId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", EVENT_HIT_CONFIRM);
        body.put("attackerId", attackerId);
        body.put("targetId", targetId);

        if (cur.armorLayer() == ArmorLayer.DODGE_IFRAME && cur.iframeUntilMs() > nowMs) {
            body.put("stagger_level", StaggerLevel.NONE.code());
            body.put("staggerLevel", "NONE");
            body.put("armorLayer", ArmorLayer.DODGE_IFRAME.name());
            body.put("iframeRemainMs", cur.iframeUntilMs() - nowMs);
            body.put("poiseBreakThreshold", 0);
            body.put("interrupted", false);
            body.put("damageTaken", false);
            body.put("invincible", true);
            return body;
        }

        if (cur.superArmor()) {
            body.put("stagger_level", StaggerLevel.NONE.code());
            body.put("staggerLevel", "NONE");
            body.put("poiseCurrent", cur.poiseCurrent());
            body.put("superArmor", true);
            body.put("armorLayer", cur.armorLayer().name());
            body.put("poiseBreakThreshold", cur.poiseBreakThreshold());
            body.put("poiseBreakThresholdRemainMs", Math.max(0, (long) cur.poiseBreakThreshold()));
            body.put("interrupted", false);
            body.put("damageTaken", cur.armorLayer() != ArmorLayer.DODGE_IFRAME);
            body.put("note", "super_armor_no_interrupt");
            return body;
        }

        double nextPoise = Math.max(0, cur.poiseCurrent() - Math.max(0, attackPoiseDamage));
        boolean broken = nextPoise <= 0;
        long defZeroUntil = cur.defenseZeroUntilMs();
        StaggerLevel stagger = StaggerLevel.LIGHT;
        if (broken) {
            nextPoise = 0;
            defZeroUntil = nowMs + DEFENSE_ZERO_MS;
            stagger = StaggerLevel.LAUNCH;
            body.put("execution", EVENT_EXECUTION_TRIGGER);
            body.put("defenseZeroMs", DEFENSE_ZERO_MS);
        } else if (attackPoiseDamage < cur.poiseMax() * 0.15) {
            stagger = StaggerLevel.NONE;
        }
        PoiseState next = new PoiseState(
                cur.entityId(), cur.poiseMax(), nextPoise,
                cur.poiseRegenPerSec(), false, defZeroUntil,
                ArmorLayer.NONE, cur.iframeUntilMs(), cur.poiseBreakThreshold(),
                stagger == StaggerLevel.NONE ? 0 : 150);
        states.put(targetId, next);
        body.put("stagger_level", stagger.code());
        body.put("staggerLevel", stagger.name());
        body.put("poiseCurrent", nextPoise);
        body.put("poiseBroken", broken);
        body.put("clientMustWaitHitConfirm", true);
        return body;
    }

    public Map<String, Object> tickRegen(long entityId, long deltaMs) {
        PoiseState cur = states.get(entityId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_found");
        }
        if (cur.poiseCurrent() <= 0) {
            return Map.of("ok", true, "poiseCurrent", 0, "regenSkipped", true);
        }
        double add = cur.poiseRegenPerSec() * (deltaMs / 1000.0);
        double next = Math.min(cur.poiseMax(), cur.poiseCurrent() + add);
        states.put(entityId, new PoiseState(
                cur.entityId(), cur.poiseMax(), next,
                cur.poiseRegenPerSec(), cur.superArmor(), cur.defenseZeroUntilMs(),
                cur.armorLayer(), cur.iframeUntilMs(), cur.poiseBreakThreshold(), cur.stiffnessRemainMs()));
        return Map.of("ok", true, "poiseCurrent", next);
    }

    public boolean defenseZeroed(long entityId, long nowMs) {
        PoiseState s = states.get(entityId);
        return s != null && s.defenseZeroUntilMs() > nowMs;
    }

    public PoiseState get(long entityId) {
        return states.get(entityId);
    }
}
