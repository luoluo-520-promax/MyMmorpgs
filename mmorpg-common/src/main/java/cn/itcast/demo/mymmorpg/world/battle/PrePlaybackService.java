package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端预表现：ACTION_START 先下发 PRE_IMPACT，严格校验后确认或 ROLLBACK。
 */
@Service
public class PrePlaybackService {

    public static final String PRE_IMPACT = "PRE_IMPACT";
    public static final String ROLLBACK = "ROLLBACK";
    public static final long STRICT_WINDOW_MS = 50L;
    public static final String DEFAULT_COMPENSATE_FX = "scratch_spark";

    private final ConcurrentHashMap<String, Long> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Float> hitBoxRadius = new ConcurrentHashMap<>();

    private static String key(long playerId, String actionId) {
        return playerId + ":" + (actionId == null ? "" : actionId.trim());
    }

    public void configureHitBoxRadius(long playerId, float radius) {
        hitBoxRadius.put(playerId, Math.max(0.1f, radius));
    }

    public float hitBoxRadiusOf(long playerId) {
        return hitBoxRadius.getOrDefault(playerId, 1.0f);
    }

    public Map<String, Object> onActionStart(long playerId, String actionId, long nowMs) {
        String k = key(playerId, actionId);
        pending.put(k, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", PRE_IMPACT);
        body.put("playerId", playerId);
        body.put("actionId", actionId);
        body.put("atMs", nowMs);
        body.put("hitBoxRadius", hitBoxRadiusOf(playerId));
        body.put("note", "client_play_hit_anticipation");
        return body;
    }

    /**
     * 严格校验 &lt;50ms：通过则确认伤害数字；失败则 ROLLBACK。
     */
    public Map<String, Object> confirmOrRollback(
            long playerId, String actionId, long actionStartMs, long confirmMs, boolean hitValid) {
        return confirmOrRollback(playerId, actionId, actionStartMs, confirmMs, hitValid, false);
    }

    /**
     * @param softRollback true 时仅回滚伤害/冷却，保留位移与动画（无 Rubber-banding）
     */
    public Map<String, Object> confirmOrRollback(
            long playerId, String actionId, long actionStartMs, long confirmMs,
            boolean hitValid, boolean softRollback) {
        String k = key(playerId, actionId);
        Long started = pending.remove(k);
        long base = started == null ? actionStartMs : started;
        long lag = Math.abs(confirmMs - base);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("playerId", playerId);
        body.put("actionId", actionId);
        body.put("lagMs", lag);
        body.put("hitBoxRadius", hitBoxRadiusOf(playerId));
        boolean latencyReject = lag > STRICT_WINDOW_MS;
        if (!hitValid || latencyReject) {
            if (softRollback) {
                body.put("ok", true);
                body.put("event", ROLLBACK);
                body.put("softRollback", true);
                body.put("predictionVerdict", HitFeedbackService.PredictionVerdict.SOFT_ROLLBACK.name());
                body.put("rollbackDamage", !hitValid);
                body.put("rollbackCooldown", latencyReject);
                body.put("keepPosition", true);
                body.put("keepAnimation", true);
                body.put("rubberBand", false);
                body.put("compensateEffectId", DEFAULT_COMPENSATE_FX);
                body.put("elasticBufferMs", 1_000L);
                body.put("forceSyncNextFrame", true);
                body.put("reason", !hitValid ? "hit_invalid_soft" : "latency_soft");
                return body;
            }
            body.put("ok", false);
            body.put("event", ROLLBACK);
            body.put("predictionVerdict", HitFeedbackService.PredictionVerdict.CONFIRMED_MISS.name());
            body.put("reason", !hitValid ? "hit_invalid" : "latency_or_cheat");
            return body;
        }
        body.put("ok", true);
        body.put("event", "IMPACT_CONFIRM");
        body.put("predictionVerdict", HitFeedbackService.PredictionVerdict.CONFIRMED_HIT.name());
        body.put("showDamageNumber", true);
        return body;
    }
}
