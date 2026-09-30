package cn.itcast.demo.mymmorpg.world.traverse;

import cn.itcast.demo.mymmorpg.sync.MovementType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 体力按动作类型 + 持续时间阶梯扣除（攀爬/游泳/滑翔等）。
 */
@Service
public class StaminaConsumeService {

    /** 空中战斗额外体力消耗（普攻/技能） */
    public static final float AIR_COMBAT_COST = 8f;
    /** 挂边恢复速率（每秒） */
    public static final float HANG_RECOVER_RATE = 10f;

    public record StaminaProfile(int baseCap, int bonusCap) {
        public int capacity() {
            return Math.max(1, baseCap + Math.max(0, bonusCap));
        }
    }

    /** 每秒基础消耗；持续越久倍率越高（阶梯）。 */
    public record CostCurve(float basePerSec, float rampPerSecAfter3s, float hardCapPerSec) {
    }

    private final ConcurrentHashMap<Long, Float> stamina = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> capBonus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> actionStartedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MovementType> lastAction = new ConcurrentHashMap<>();
    private final EnumMap<MovementType, CostCurve> curves = new EnumMap<>(MovementType.class);
    private int baseCap = 100;

    public StaminaConsumeService() {
        curves.put(MovementType.WALK, new CostCurve(0f, 0f, 0f));
        curves.put(MovementType.DASH, new CostCurve(12f, 2f, 24f));
        curves.put(MovementType.SWIM, new CostCurve(8f, 1.5f, 18f));
        curves.put(MovementType.CLIMB, new CostCurve(10f, 2.5f, 22f));
        curves.put(MovementType.GLIDE, new CostCurve(6f, 1.0f, 14f));
        curves.put(MovementType.SWING, new CostCurve(14f, 3f, 28f));
        curves.put(MovementType.GRAPPLE, new CostCurve(16f, 2f, 28f));
        curves.put(MovementType.RIDE, new CostCurve(4f, 0.5f, 10f));
        curves.put(MovementType.AIR_DASH, new CostCurve(0f, 0f, 0f)); // 固定点耗，见 consumeFixed
        curves.put(MovementType.WALL_RUN, new CostCurve(15f, 2f, 24f)); // 每秒 15 点
        curves.put(MovementType.CLIMB_HANG, new CostCurve(0f, 0f, 0f)); // 挂边不持续扣体，由 tickHangRecover 恢复
        curves.put(MovementType.CLIMB_VAULT, new CostCurve(0f, 0f, 0f));
    }

    /** 空中战斗固定体力消耗。 */
    public Map<String, Object> consumeAirCombat(long playerId, long nowMs) {
        return consumeFixed(playerId, MovementType.AIR_DASH, AIR_COMBAT_COST, nowMs);
    }

    /** 挂边每秒恢复体力。 */
    public Map<String, Object> tickHangRecover(long playerId, long deltaMs) {
        float add = HANG_RECOVER_RATE * Math.max(0.016f, deltaMs / 1000f);
        float next = recover(playerId, add);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("stamina", next);
        body.put("recovered", add);
        body.put("hangRecoverRate", HANG_RECOVER_RATE);
        return body;
    }

    /** 空中冲刺等固定体力消耗（默认 20 点）。 */
    public Map<String, Object> consumeFixed(long playerId, MovementType type, float amount, long nowMs) {
        if (playerId <= 0) {
            return Map.of("ok", false, "error", "invalid_player");
        }
        ensureFilled(playerId);
        float cost = amount <= 0f ? 20f : amount;
        float before = current(playerId);
        if (before < cost) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("error", "stamina_exhausted");
            body.put("stamina", before);
            body.put("required", cost);
            body.put("movementType", type == null ? MovementType.AIR_DASH.name() : type.name());
            return body;
        }
        float after = before - cost;
        stamina.put(playerId, after);
        lastAction.put(playerId, type == null ? MovementType.AIR_DASH : type);
        actionStartedAt.put(playerId, nowMs);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("stamina", after);
        body.put("consumed", cost);
        body.put("movementType", type == null ? MovementType.AIR_DASH.name() : type.name());
        body.put("capacity", profile(playerId).capacity());
        return body;
    }

    public void configureBaseCap(int cap) {
        this.baseCap = Math.max(10, cap);
    }

    public void addCapBonus(long playerId, int bonus) {
        if (playerId <= 0 || bonus == 0) {
            return;
        }
        capBonus.computeIfAbsent(playerId, id -> new AtomicInteger(0)).addAndGet(bonus);
        ensureFilled(playerId);
    }

    public StaminaProfile profile(long playerId) {
        int bonus = capBonus.getOrDefault(playerId, new AtomicInteger(0)).get();
        return new StaminaProfile(baseCap, bonus);
    }

    public float current(long playerId) {
        ensureFilled(playerId);
        return stamina.getOrDefault(playerId, (float) profile(playerId).capacity());
    }

    /**
     * 按动作持续时长阶梯扣体力；不足则拒绝该动作。
     *
     * @param durationMs 本段动作时长（通常为两次移动包间隔）
     * @param costScale  额外倍率（世界技减耗可传 &lt;1）
     */
    public Map<String, Object> consume(
            long playerId, MovementType type, long durationMs, float costScale, long nowMs) {
        if (playerId <= 0) {
            return Map.of("ok", false, "error", "invalid_player");
        }
        MovementType action = type == null ? MovementType.WALK : type;
        ensureFilled(playerId);
        CostCurve curve = curves.getOrDefault(action, curves.get(MovementType.WALK));
        if (curve.basePerSec() <= 0f) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("stamina", current(playerId));
            body.put("consumed", 0f);
            body.put("movementType", action.name());
            return body;
        }

        MovementType prev = lastAction.get(playerId);
        if (prev != action) {
            actionStartedAt.put(playerId, nowMs);
            lastAction.put(playerId, action);
        }
        long started = actionStartedAt.getOrDefault(playerId, nowMs);
        float sustainedSec = Math.max(0f, (nowMs - started) / 1000f);
        float dt = Math.max(0.016f, durationMs / 1000f);
        float perSec = curve.basePerSec();
        if (sustainedSec > 3f) {
            perSec += curve.rampPerSecAfter3s() * (sustainedSec - 3f);
        }
        perSec = Math.min(curve.hardCapPerSec(), perSec);
        float scale = costScale <= 0f ? 1f : costScale;
        float cost = perSec * dt * scale;

        float before = current(playerId);
        if (before < cost) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("error", "stamina_exhausted");
            body.put("stamina", before);
            body.put("required", cost);
            body.put("movementType", action.name());
            return body;
        }
        float after = before - cost;
        stamina.put(playerId, after);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("stamina", after);
        body.put("consumed", Math.round(cost * 100f) / 100f);
        body.put("perSec", Math.round(perSec * 100f) / 100f);
        body.put("sustainedSec", Math.round(sustainedSec * 100f) / 100f);
        body.put("movementType", action.name());
        body.put("capacity", profile(playerId).capacity());
        return body;
    }

    public float recover(long playerId, float amount) {
        ensureFilled(playerId);
        float cap = profile(playerId).capacity();
        float next = Math.min(cap, current(playerId) + Math.max(0f, amount));
        stamina.put(playerId, next);
        return next;
    }

    /** 允许扣至负值（歇脚点首次跳跃透支窗口）。 */
    public float consumeAllowNegative(long playerId, float amount) {
        ensureFilled(playerId);
        float next = current(playerId) - Math.max(0f, amount);
        stamina.put(playerId, next);
        return next;
    }

    public Map<String, Object> snapshot(long playerId) {
        StaminaProfile p = profile(playerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("stamina", current(playerId));
        body.put("capacity", p.capacity());
        body.put("baseCap", p.baseCap());
        body.put("bonusCap", p.bonusCap());
        body.put("lastAction", lastAction.getOrDefault(playerId, MovementType.WALK).name());
        return body;
    }

    private void ensureFilled(long playerId) {
        stamina.computeIfAbsent(playerId, id -> (float) profile(id).capacity());
    }
}
