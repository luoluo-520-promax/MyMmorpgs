package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 瞬时博弈反馈校验：完美闪避 / 弹反窗口由服务端下发并基于时间戳校验，禁止客户端自报。
 */
@Service
public class ReactionValidator {

    public static final int DEFAULT_DODGE_WINDOW_MS = 200;
    public static final int DEFAULT_PARRY_WINDOW_MS = 180;
    /** 动态窗口：BaseWindow + RTT * 0.5 */
    public static final double RTT_WINDOW_FACTOR = 0.5d;
    /** 客户端时间戳背书弹性：网络晚到 ±100ms 仍判定成功 */
    public static final int CLIENT_TIMESTAMP_BUFFER_MS = 100;
    public static final long BULLET_TIME_DURATION_MS = 1500L;
    public static final double BULLET_TIME_SCALE = 0.1d;

    public enum ReactionKind {
        PERFECT_DODGE, PARRY
    }

    public record AttackWindow(
            String attackId,
            long attackerEntityId,
            long openAtMs,
            int dodgeWindowMs,
            int parryWindowMs) {
    }

    private final ConcurrentHashMap<String, AttackWindow> windows = new ConcurrentHashMap<>();
    /** battleId → 完美闪避次数（结算评分） */
    private final ConcurrentHashMap<String, AtomicInteger> perfectDodgeCounts = new ConcurrentHashMap<>();
    /** playerId → 当前动作取消优先级 */
    private final ConcurrentHashMap<Long, CancelAction> currentAction = new ConcurrentHashMap<>();
    private final BulletTimeService bulletTime;

    public ReactionValidator() {
        this(new BulletTimeService());
    }

    public ReactionValidator(BulletTimeService bulletTime) {
        this.bulletTime = bulletTime == null ? new BulletTimeService() : bulletTime;
    }

    /**
     * 攻击出手时由服务端开启窗口，并下发 dodgeWindowMs / parryWindowMs。
     */
    public int dynamicDodgeWindowMs(int baseWindowMs, int playerRttMs) {
        int base = baseWindowMs > 0 ? baseWindowMs : DEFAULT_DODGE_WINDOW_MS;
        int rtt = Math.max(0, playerRttMs);
        return base + (int) Math.round(rtt * RTT_WINDOW_FACTOR);
    }

    public Map<String, Object> openAttackWindow(
            String attackId, long attackerEntityId, long nowMs,
            int dodgeWindowMs, int parryWindowMs) {
        return openAttackWindow(attackId, attackerEntityId, nowMs, dodgeWindowMs, parryWindowMs, 0);
    }

    public Map<String, Object> openAttackWindow(
            String attackId, long attackerEntityId, long nowMs,
            int dodgeWindowMs, int parryWindowMs, int playerRttMs) {
        if (attackId == null || attackId.isBlank()) {
            return Map.of("ok", false, "error", "attack_id_required");
        }
        int dodge = dynamicDodgeWindowMs(dodgeWindowMs, playerRttMs);
        int parry = parryWindowMs > 0 ? parryWindowMs : DEFAULT_PARRY_WINDOW_MS;
        AttackWindow w = new AttackWindow(attackId.trim(), attackerEntityId, nowMs, dodge, parry);
        windows.put(w.attackId(), w);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("attackId", w.attackId());
        body.put("attackerEntityId", attackerEntityId);
        body.put("openAtMs", nowMs);
        body.put("dodgeWindowMs", dodge);
        body.put("parryWindowMs", parry);
        body.put("playerRttMs", playerRttMs);
        body.put("dynamicWindow", playerRttMs > 0);
        return body;
    }

    /**
     * 校验 ACTION_PERFECT_DODGE / ACTION_PARRY：优先 ClientActionTimestamp，给予 ±100ms 弹性 Buffer。
     */
    public Map<String, Object> validate(
            ReactionKind kind, String battleId, long playerId,
            String attackId, long clientTs, long nowMs) {
        if (kind == null) {
            return Map.of("ok", false, "error", "kind_required");
        }
        AttackWindow w = windows.get(attackId == null ? "" : attackId.trim());
        if (w == null) {
            return Map.of("ok", false, "error", "window_not_found", "kind", kind.name());
        }
        long ts = clientTs > 0 ? clientTs : nowMs;
        int windowMs = kind == ReactionKind.PERFECT_DODGE ? w.dodgeWindowMs() : w.parryWindowMs();
        int effectiveWindow = windowMs + CLIENT_TIMESTAMP_BUFFER_MS;
        long skew = Math.abs(ts - w.openAtMs());
        boolean withinClientWindow = ts >= w.openAtMs() - CLIENT_TIMESTAMP_BUFFER_MS
                && ts <= w.openAtMs() + effectiveWindow;
        if (skew > effectiveWindow && !withinClientWindow) {
            Map<String, Object> reject = new LinkedHashMap<>();
            reject.put("ok", false);
            reject.put("error", "outside_window");
            reject.put("kind", kind.name());
            reject.put("skewMs", skew);
            reject.put("windowMs", windowMs);
            reject.put("clientTimestampBufferMs", CLIENT_TIMESTAMP_BUFFER_MS);
            reject.put("clientActionTimestamp", clientTs);
            return reject;
        }
        windows.remove(w.attackId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("kind", kind.name());
        body.put("attackId", w.attackId());
        body.put("playerId", playerId);
        body.put("attackerEntityId", w.attackerEntityId());
        body.put("clientTimestampBacked", clientTs > 0);
        body.put("clientTimestampBufferMs", CLIENT_TIMESTAMP_BUFFER_MS);
        if (kind == ReactionKind.PERFECT_DODGE) {
            String bid = battleId == null || battleId.isBlank() ? "local" : battleId.trim();
            int count = perfectDodgeCounts.computeIfAbsent(bid, id -> new AtomicInteger(0))
                    .incrementAndGet();
            body.put("perfectDodgeCount", count);
            Map<String, Object> bt = bulletTime.start(
                    bid, playerId, w.attackerEntityId(), nowMs,
                    BULLET_TIME_DURATION_MS, BULLET_TIME_SCALE);
            body.put("bulletTimeStart", bt);
            body.put("event", "bulletTimeStart");
        } else {
            body.put("event", "parrySuccess");
            body.put("stunAttackerMs", 800);
        }
        return body;
    }

    public int perfectDodgeCount(String battleId) {
        String bid = battleId == null || battleId.isBlank() ? "local" : battleId.trim();
        AtomicInteger c = perfectDodgeCounts.get(bid);
        return c == null ? 0 : c.get();
    }

    /** 结算评分：完美闪避计入掉落权重加成（每段 +2%，上限 20%）。 */
    public Map<String, Object> settleScore(String battleId) {
        int dodges = perfectDodgeCount(battleId);
        double lootWeightBonus = Math.min(0.20d, dodges * 0.02d);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("battleId", battleId);
        body.put("perfectDodgeCount", dodges);
        body.put("lootWeightBonus", lootWeightBonus);
        return body;
    }

    public BulletTimeService bulletTime() {
        return bulletTime;
    }

    public void setCurrentAction(long playerId, CancelAction action) {
        if (playerId > 0 && action != null) {
            currentAction.put(playerId, action);
        }
    }

    public CancelAction currentAction(long playerId) {
        return currentAction.getOrDefault(playerId, CancelAction.NONE);
    }

    /**
     * 取消表校验：HIT_CONFIRM 硬直期间，若新指令优先级高于当前动作则允许立刻中断后摇。
     */
    public Map<String, Object> tryCancel(
            long playerId, CancelAction expectedCancel, PoiseService poise, long nowMs) {
        CancelAction current = currentAction.getOrDefault(playerId, CancelAction.NONE);
        CancelAction incoming = expectedCancel == null ? CancelAction.NONE : expectedCancel;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("currentAction", current.name());
        body.put("incomingAction", incoming.name());
        long stiffness = poise == null ? 0 : poise.stiffnessRemainMs(playerId);
        body.put("stiffnessRemainMs", stiffness);
        if (stiffness <= 0) {
            body.put("cancelAllowed", true);
            body.put("reason", "no_stiffness");
            currentAction.put(playerId, incoming);
            return body;
        }
        if (incoming.priority() > current.priority()) {
            if (poise != null) {
                body.put("poiseReset", poise.resetStiffness(playerId, incoming));
            }
            body.put("cancelAllowed", true);
            body.put("event", "CANCEL_OVERRIDE");
            currentAction.put(playerId, incoming);
            return body;
        }
        body.put("cancelAllowed", false);
        body.put("retcode", cn.itcast.demo.mymmorpg.protocol.RetCode.CANCEL_PRIORITY_DENIED);
        return body;
    }
}
