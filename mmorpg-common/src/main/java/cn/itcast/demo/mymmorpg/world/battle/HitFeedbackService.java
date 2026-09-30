package cn.itcast.demo.mymmorpg.world.battle;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 命中反馈：HitStop + CameraShake + PredictionVerdict，权威下发 MSG_BATTLE_HIT_FEEDBACK。
 */
@Service
public class HitFeedbackService {

    public static final int HIT_STOP_MIN_MS = 50;
    public static final int HIT_STOP_MAX_MS = 150;
    public static final float DEFAULT_HITBOX_RADIUS = 1.0f;
    public static final String MISS_SCRAPE_EFFECT = "scratch_spark";

    public enum PredictionVerdict {
        CONFIRMED_HIT, CONFIRMED_MISS, SOFT_ROLLBACK
    }

    public Map<String, Object> buildFeedback(
            long attackerId,
            long targetId,
            double poiseDamage,
            boolean heavyOrFall,
            float targetPoiseRemainRatio) {
        return buildFeedback(attackerId, targetId, poiseDamage, heavyOrFall,
                targetPoiseRemainRatio, PredictionVerdict.CONFIRMED_HIT, null, 1.0f, !heavyOrFall);
    }

    public Map<String, Object> buildFeedback(
            long attackerId,
            long targetId,
            double poiseDamage,
            boolean heavyOrFall,
            float targetPoiseRemainRatio,
            PredictionVerdict verdict,
            String compensateEffectId,
            float hitBoxScale) {
        return buildFeedback(attackerId, targetId, poiseDamage, heavyOrFall,
                targetPoiseRemainRatio, verdict, compensateEffectId, hitBoxScale, !heavyOrFall);
    }

    public Map<String, Object> buildFeedback(
            long attackerId,
            long targetId,
            double poiseDamage,
            boolean heavyOrFall,
            float targetPoiseRemainRatio,
            PredictionVerdict verdict,
            String compensateEffectId,
            float hitBoxScale,
            boolean allowCancelDuringHitStop) {
        double stagger = Math.max(0, Math.min(1.0, 1.0 - targetPoiseRemainRatio));
        int hitStop = HIT_STOP_MIN_MS
                + (int) Math.round((HIT_STOP_MAX_MS - HIT_STOP_MIN_MS) * stagger);
        if (poiseDamage > 40) {
            hitStop = Math.min(HIT_STOP_MAX_MS, hitStop + 20);
        }
        float shake = heavyOrFall ? 0.85f : 0.35f + (float) (stagger * 0.4);
        float scale = hitBoxScale <= 0 ? 1.0f : hitBoxScale;
        PredictionVerdict v = verdict == null ? PredictionVerdict.CONFIRMED_HIT : verdict;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("msgId", MessageId.BATTLE_HIT_FEEDBACK_SC_NOTIFY);
        body.put("event", "MSG_BATTLE_HIT_FEEDBACK");
        body.put("attackerId", attackerId);
        body.put("targetId", targetId);
        body.put("predictionVerdict", v.name());
        body.put("hitBoxRadius", Math.round(DEFAULT_HITBOX_RADIUS * scale * 1000.0) / 1000.0);
        body.put("hitBoxScale", scale);
        if (v == PredictionVerdict.SOFT_ROLLBACK) {
            body.put("hitStopDurationMs", 0);
            body.put("shakeIntensity", 0.15);
            body.put("keepAnimation", true);
            body.put("compensateEffectId",
                    compensateEffectId == null || compensateEffectId.isBlank()
                            ? MISS_SCRAPE_EFFECT : compensateEffectId);
            body.put("note", "soft_rollback_miss_scrape_fx");
        } else if (v == PredictionVerdict.CONFIRMED_MISS) {
            body.put("hitStopDurationMs", 0);
            body.put("shakeIntensity", 0.1);
            body.put("compensateEffectId", MISS_SCRAPE_EFFECT);
            body.put("showDamageNumber", false);
        } else {
            body.put("hitStopDurationMs", hitStop);
            body.put("shakeIntensity", Math.round(shake * 100.0) / 100.0);
            body.put("pause_gameplay_tick", true);
            body.put("pausePhysics", false);
            body.put("skillCdUsesWallClock", true);
            body.put("buffTimerUsesWallClock", true);
            body.put("allowCancelDuringHitStop", allowCancelDuringHitStop);
            body.put("note", allowCancelDuringHitStop
                    ? "light_hit_dodge_cancel_allowed" : "boss_heavy_hit_no_cancel");
        }
        return body;
    }
}
