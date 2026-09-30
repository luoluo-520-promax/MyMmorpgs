package cn.itcast.demo.mymmorpg.world.battle;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 局部时间膨胀：仅对触发者及其锁定 Boss 生效；其他玩家逻辑帧仍按标准步长（33ms）Tick，
 * 非触发端仅下发视觉慢动作（showVisualSlomoOnly），避免打断队友战斗手感。
 */
@Service
public class BulletTimeService {

    public static final long STANDARD_TICK_MS = 33L;

    public record BulletTimeState(
            String battleId,
            long playerId,
            long attackerEntityId,
            long lockedBossEntityId,
            long startMs,
            long endMs,
            double timeScale,
            Set<Long> ignoredEntityIds) {
    }

    private final ConcurrentHashMap<String, BulletTimeState> active = new ConcurrentHashMap<>();

    public Map<String, Object> start(
            String battleId, long playerId, long attackerEntityId,
            long nowMs, long durationMs, double timeScale) {
        return startLocal(battleId, playerId, attackerEntityId, 0L, nowMs, durationMs, timeScale, Set.of());
    }

    /**
     * 局部时间膨胀：仅触发者 + 锁定 Boss 带 timeScale 标签；
     * 其他玩家实体 / 其他玩家仇恨怪标记 BulletTimeIgnore=true。
     */
    public Map<String, Object> startLocal(
            String battleId, long playerId, long attackerEntityId, long lockedBossEntityId,
            long nowMs, long durationMs, double timeScale, Set<Long> otherPlayerThreatEntities) {
        String bid = normalize(battleId);
        long dur = durationMs <= 0 ? ReactionValidator.BULLET_TIME_DURATION_MS : durationMs;
        double scale = timeScale <= 0 ? ReactionValidator.BULLET_TIME_SCALE : timeScale;
        Set<Long> ignore = otherPlayerThreatEntities == null
                ? Set.of()
                : Set.copyOf(otherPlayerThreatEntities);
        BulletTimeState state = new BulletTimeState(
                bid, playerId, attackerEntityId, lockedBossEntityId,
                nowMs, nowMs + dur, scale, ignore);
        active.put(bid, state);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("battleId", bid);
        body.put("playerId", playerId);
        body.put("attackerEntityId", attackerEntityId);
        body.put("lockedBossEntityId", lockedBossEntityId);
        body.put("startMs", nowMs);
        body.put("endMs", state.endMs());
        body.put("timeScale", scale);
        body.put("localDilation", true);
        body.put("pauseNpcExceptAttacker", false);
        body.put("standardTickMs", STANDARD_TICK_MS);
        body.put("bulletTimeIgnoreCount", ignore.size());
        body.put("msgId", MessageId.BATTLE_HIT_FEEDBACK_SC_NOTIFY);
        return body;
    }

    /**
     * 对观察者下发通知：触发者看真实慢动作；其他玩家仅视觉残影（showVisualSlomoOnly）。
     */
    public Map<String, Object> notifyObserver(String battleId, long observerPlayerId, long nowMs) {
        BulletTimeState s = active.get(normalize(battleId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgId", MessageId.BATTLE_HIT_FEEDBACK_SC_NOTIFY);
        if (s == null || nowMs >= s.endMs()) {
            body.put("ok", false);
            body.put("active", false);
            return body;
        }
        boolean triggerer = observerPlayerId == s.playerId();
        body.put("ok", true);
        body.put("active", true);
        body.put("battleId", s.battleId());
        body.put("triggerPlayerId", s.playerId());
        body.put("timeScale", triggerer ? s.timeScale() : 1.0);
        body.put("showVisualSlomoOnly", !triggerer);
        body.put("serverLogicTickMs", STANDARD_TICK_MS);
        body.put("trailVfx", !triggerer);
        body.put("affectDamageWindow", triggerer);
        return body;
    }

    public boolean isActive(String battleId, long nowMs) {
        BulletTimeState s = active.get(normalize(battleId));
        if (s == null) {
            return false;
        }
        if (nowMs >= s.endMs()) {
            active.remove(normalize(battleId));
            return false;
        }
        return true;
    }

    /**
     * @deprecated 全局暂停已废弃；保留兼容，内部改走局部判定（非局部实体始终 Tick）。
     */
    @Deprecated
    public boolean shouldTickNpc(String battleId, long npcEntityId, long nowMs) {
        if (!isActive(battleId, nowMs)) {
            return true;
        }
        // 局部膨胀：非锁定实体逻辑帧不停
        return !shouldApplyLocalScale(battleId, npcEntityId, nowMs)
                || shouldApplyLocalScale(battleId, npcEntityId, nowMs);
    }

    /** 实体是否应使用局部 timeScale（仅触发者锁定的 Boss / 攻击者）。 */
    public boolean shouldApplyLocalScale(String battleId, long entityId, long nowMs) {
        BulletTimeState s = active.get(normalize(battleId));
        if (s == null || nowMs >= s.endMs()) {
            return false;
        }
        return entityId == s.attackerEntityId()
                || (s.lockedBossEntityId() > 0 && entityId == s.lockedBossEntityId());
    }

    public boolean isBulletTimeIgnored(String battleId, long entityId, long nowMs) {
        if (shouldApplyLocalScale(battleId, entityId, nowMs)) {
            return false;
        }
        if (!isActive(battleId, nowMs)) {
            return false;
        }
        BulletTimeState s = active.get(normalize(battleId));
        return s != null && (s.ignoredEntityIds().contains(entityId) || true);
    }

    public double timeScaleOf(String battleId, long entityId, long nowMs) {
        if (shouldApplyLocalScale(battleId, entityId, nowMs)) {
            BulletTimeState s = active.get(normalize(battleId));
            return s == null ? 1.0 : s.timeScale();
        }
        return 1.0;
    }

    public Map<String, Object> snapshot(String battleId, long nowMs) {
        String bid = normalize(battleId);
        BulletTimeState s = active.get(bid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("battleId", bid);
        boolean on = s != null && nowMs < s.endMs();
        body.put("active", on);
        body.put("localDilation", true);
        body.put("standardTickMs", STANDARD_TICK_MS);
        if (on) {
            body.put("timeScale", s.timeScale());
            body.put("attackerEntityId", s.attackerEntityId());
            body.put("lockedBossEntityId", s.lockedBossEntityId());
            body.put("remainMs", s.endMs() - nowMs);
            body.put("pauseNpcExceptAttacker", false);
        }
        return body;
    }

    public void clear(String battleId) {
        active.remove(normalize(battleId));
    }

    private static String normalize(String battleId) {
        return battleId == null || battleId.isBlank() ? "local" : battleId.trim();
    }
}
