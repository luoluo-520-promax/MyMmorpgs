package cn.itcast.demo.mymmorpg.anticheat;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 反作弊底座：移动超速/瞬移、战斗伤害上限校验，以及违规计数与封禁判定。
 * 可委托 {@link AntiCheatRuleEngine} 做可配置规则评估。
 */
@Component
public class AntiCheatService {

    public enum Verdict {
        OK, REJECT, STRIKE, BAN
    }

    public record CheckResult(Verdict verdict, String reason, int strikeCount) {
        public static CheckResult ok() {
            return new CheckResult(Verdict.OK, "", 0);
        }
    }

    private final ConcurrentHashMap<Long, AtomicInteger> strikes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> bannedUntil = new ConcurrentHashMap<>();
    private final AntiCheatRuleEngine ruleEngine;
    private final BehaviorAnomalyDetector anomalyDetector;

    private volatile float maxMoveSpeed = 120f;
    private volatile float maxTeleportDistance = 400f;
    private volatile int strikeBanThreshold = 5;
    private volatile long banDurationMs = 300_000L;
    private volatile double maxDamageMultiplier = 3.0;

    public AntiCheatService() {
        this((AntiCheatRuleEngine) null);
    }

    @Autowired
    public AntiCheatService(ObjectProvider<AntiCheatRuleEngine> ruleEngineProvider) {
        this(ruleEngineProvider == null ? null : ruleEngineProvider.getIfAvailable());
    }

    public AntiCheatService(AntiCheatRuleEngine ruleEngine) {
        this(ruleEngine, new BehaviorAnomalyDetector());
    }

    public AntiCheatService(AntiCheatRuleEngine ruleEngine, BehaviorAnomalyDetector anomalyDetector) {
        this.ruleEngine = ruleEngine;
        this.anomalyDetector = anomalyDetector == null ? new BehaviorAnomalyDetector() : anomalyDetector;
        if (this.ruleEngine != null && this.ruleEngine.listRules().isEmpty()) {
            this.ruleEngine.loadRules(AntiCheatRuleEngine.defaultRules());
        }
    }

    public AntiCheatRuleEngine ruleEngine() {
        return ruleEngine;
    }

    public BehaviorAnomalyDetector anomalyDetector() {
        return anomalyDetector;
    }

    public void configure(float maxMoveSpeed, float maxTeleportDistance, int strikeBanThreshold,
                          long banDurationMs, double maxDamageMultiplier) {
        this.maxMoveSpeed = Math.max(1f, maxMoveSpeed);
        this.maxTeleportDistance = Math.max(1f, maxTeleportDistance);
        this.strikeBanThreshold = Math.max(1, strikeBanThreshold);
        this.banDurationMs = Math.max(1_000L, banDurationMs);
        this.maxDamageMultiplier = Math.max(1.0, maxDamageMultiplier);
        syncEngineThresholds();
    }

    public boolean isBanned(long playerId) {
        Long until = bannedUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            bannedUntil.remove(playerId, until);
            return false;
        }
        return true;
    }

    public CheckResult checkMove(long playerId, float fromX, float fromY, float fromZ,
                                 float toX, float toY, float toZ, float reportedSpeed,
                                 long clientTimestamp, long serverNow) {
        if (isBanned(playerId)) {
            return new CheckResult(Verdict.BAN, "player_banned", strikeCount(playerId));
        }
        if (reportedSpeed <= 0) {
            return strike(playerId, "speed_invalid");
        }
        float dx = toX - fromX;
        float dy = toY - fromY;
        float dz = toZ - fromZ;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (ruleEngine != null) {
            AntiCheatRuleEngine.CheckResult engine = ruleEngine.evaluate(playerId, Map.of(
                    "reportedSpeed", reportedSpeed,
                    "moveSpeed", reportedSpeed,
                    "distance", dist,
                    "teleportDistance", dist));
            if (engine.verdict() != Verdict.OK) {
                return applyEngineVerdict(playerId, engine);
            }
        } else {
            if (reportedSpeed > maxMoveSpeed) {
                return strike(playerId, "speed_invalid");
            }
            if (dist > maxTeleportDistance) {
                return strike(playerId, "teleport");
            }
        }
        if (Math.abs(serverNow - clientTimestamp) > 60_000L) {
            return strike(playerId, "timestamp_skew");
        }
        anomalyDetector.record(playerId, "move_jitter", dist, serverNow);
        return CheckResult.ok();
    }

    /**
     * 记录操作样本并评估行为异常（供技能/点击等入口调用）。
     */
    public CheckResult checkBehaviorAnomaly(long playerId, String kind, double value, long nowMs) {
        if (isBanned(playerId)) {
            return new CheckResult(Verdict.BAN, "player_banned", strikeCount(playerId));
        }
        anomalyDetector.record(playerId, kind, value, nowMs);
        BehaviorAnomalyDetector.AnomalyResult r = anomalyDetector.evaluate(playerId, nowMs);
        if (r.suspicious()) {
            return strike(playerId, "behavior_anomaly:" + r.reason());
        }
        return CheckResult.ok();
    }

    /**
     * 战斗伤害校验：actualDamage 不得超过 expectedMax * multiplier。
     */
    public CheckResult checkDamage(long playerId, int expectedMaxDamage, int actualDamage) {
        if (isBanned(playerId)) {
            return new CheckResult(Verdict.BAN, "player_banned", strikeCount(playerId));
        }
        if (expectedMaxDamage < 0 || actualDamage < 0) {
            return strike(playerId, "damage_negative");
        }
        if (ruleEngine != null) {
            AntiCheatRuleEngine.CheckResult engine = ruleEngine.evaluate(playerId, Map.of(
                    "actualDamage", actualDamage,
                    "damage", actualDamage,
                    "expectedMaxDamage", expectedMaxDamage));
            if (engine.verdict() != Verdict.OK) {
                return applyEngineVerdict(playerId, engine);
            }
            return CheckResult.ok();
        }
        double cap = expectedMaxDamage * maxDamageMultiplier;
        if (actualDamage > cap + 1) {
            return strike(playerId, "damage_overflow");
        }
        return CheckResult.ok();
    }

    /**
     * 直接委托规则引擎评估任意上下文（如打击频率），违规时计入 strike。
     */
    public CheckResult evaluateRules(long playerId, Map<String, ?> context) {
        if (isBanned(playerId)) {
            return new CheckResult(Verdict.BAN, "player_banned", strikeCount(playerId));
        }
        if (ruleEngine == null) {
            return CheckResult.ok();
        }
        AntiCheatRuleEngine.CheckResult engine = ruleEngine.evaluate(playerId, context);
        if (engine.verdict() == Verdict.OK) {
            return CheckResult.ok();
        }
        return applyEngineVerdict(playerId, engine);
    }

    public CheckResult strike(long playerId, String reason) {
        int n = strikes.computeIfAbsent(playerId, id -> new AtomicInteger()).incrementAndGet();
        if (n >= strikeBanThreshold) {
            bannedUntil.put(playerId, System.currentTimeMillis() + banDurationMs);
            return new CheckResult(Verdict.BAN, reason, n);
        }
        return new CheckResult(Verdict.STRIKE, reason, n);
    }

    public int strikeCount(long playerId) {
        AtomicInteger n = strikes.get(playerId);
        return n == null ? 0 : n.get();
    }

    public void clear(long playerId) {
        strikes.remove(playerId);
        bannedUntil.remove(playerId);
        anomalyDetector.clear(playerId);
    }

    public Map<String, Object> snapshot(long playerId) {
        Long until = bannedUntil.get(playerId);
        return Map.of(
                "playerId", playerId,
                "strikes", strikeCount(playerId),
                "banned", isBanned(playerId),
                "bannedUntilMs", until == null ? 0L : until);
    }

    private CheckResult applyEngineVerdict(long playerId, AntiCheatRuleEngine.CheckResult engine) {
        if (engine.verdict() == Verdict.BAN) {
            bannedUntil.put(playerId, System.currentTimeMillis() + banDurationMs);
            return new CheckResult(Verdict.BAN, engine.reason(), strikeCount(playerId));
        }
        if (engine.verdict() == Verdict.REJECT) {
            return new CheckResult(Verdict.REJECT, engine.reason(), strikeCount(playerId));
        }
        return strike(playerId, engine.reason());
    }

    private void syncEngineThresholds() {
        if (ruleEngine == null) {
            return;
        }
        if (ruleEngine.listRules().isEmpty()) {
            ruleEngine.loadRules(AntiCheatRuleEngine.defaultRules());
        }
        ruleEngine.updateThreshold("move_speed", maxMoveSpeed);
        ruleEngine.updateThreshold("teleport", maxTeleportDistance);
        ruleEngine.updateThreshold("damage", maxDamageMultiplier);
        ruleEngine.updateThreshold("strike_rate", strikeBanThreshold);
    }
}
