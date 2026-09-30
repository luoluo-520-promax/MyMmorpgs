package cn.itcast.demo.mymmorpg.dda;

import cn.itcast.demo.mymmorpg.ai.AiDecisionLogger;
import cn.itcast.demo.mymmorpg.experiment.ExperimentAssigner;
import cn.itcast.demo.mymmorpg.metrics.AiMetrics;
import cn.itcast.demo.mymmorpg.telemetry.TrainingDataCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis（或内存）历史战斗统计的动态难度调节。
 */
@Service
public class DynamicDifficultyService {

    private static final Logger log = LoggerFactory.getLogger(DynamicDifficultyService.class);
    private static final String REDIS_KEY = "battle:dda:stats:";
    private static final Duration TTL = Duration.ofDays(14);

    private final DifficultyEvaluator evaluator = new DifficultyEvaluator();
    private final ConcurrentHashMap<Long, RunningStats> local = new ConcurrentHashMap<>();
    private final AiMetrics aiMetrics;
    private final AiDecisionLogger decisionLogger;
    private final TrainingDataCollector trainingData;
    private StringRedisTemplate redis;

    public DynamicDifficultyService() {
        this(new AiMetrics(), new AiDecisionLogger(), new TrainingDataCollector());
    }

    public DynamicDifficultyService(AiMetrics aiMetrics, AiDecisionLogger decisionLogger,
                                    TrainingDataCollector trainingData) {
        this.aiMetrics = aiMetrics == null ? new AiMetrics() : aiMetrics;
        this.decisionLogger = decisionLogger == null ? new AiDecisionLogger() : decisionLogger;
        this.trainingData = trainingData == null ? new TrainingDataCollector() : trainingData;
    }

    @Autowired
    public void bindRedis(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    /**
     * 战斗结束后写入样本。
     */
    public void recordBattleResult(long playerId, double dps, boolean survived, long clearTimeMs) {
        if (playerId <= 0) {
            return;
        }
        RunningStats s = load(playerId);
        s.samples++;
        s.dpsSum += Math.max(0.0, dps);
        if (survived) {
            s.surviveCount++;
        }
        s.clearTimeSumMs += Math.max(0L, clearTimeMs);
        save(playerId, s);
        trainingData.collect("battle_result", playerId, Map.of(
                "dps", dps,
                "survived", survived,
                "clearTimeMs", clearTimeMs));
    }

    public DifficultyEvaluator.Adjustment evaluate(long playerId) {
        String variant = ExperimentAssigner.assign(playerId, "dda_algo", "baseline", "aggressive");
        RunningStats s = load(playerId);
        DifficultyEvaluator.BattleStats stats;
        if (s.samples <= 0) {
            stats = new DifficultyEvaluator.BattleStats(1000, 0.7, 180_000, 180_000, 1000);
        } else {
            double avgDps = s.dpsSum / s.samples;
            double survival = (double) s.surviveCount / s.samples;
            long avgClear = s.clearTimeSumMs / s.samples;
            stats = new DifficultyEvaluator.BattleStats(avgDps, survival, avgClear, 180_000, 1000);
        }
        DifficultyEvaluator.Adjustment adj = evaluator.evaluate(stats);
        if ("aggressive".equals(variant)) {
            adj = new DifficultyEvaluator.Adjustment(
                    round2(adj.skillFrequencyMul() * 1.08),
                    round2(adj.mobCountMul() * 1.05),
                    adj.rewardMul(),
                    round2(adj.overallDifficulty() * 1.05),
                    adj.reason() + "+ab_aggressive",
                    adj.weights());
        }
        aiMetrics.recordDecision("dda");
        Map<String, Object> weightObj = new LinkedHashMap<>();
        if (adj.weights() != null) {
            weightObj.putAll(adj.weights());
        }
        decisionLogger.log("dda", playerId, "adjust", adj.reason(), weightObj, Map.of(
                "skillFrequencyMul", adj.skillFrequencyMul(),
                "mobCountMul", adj.mobCountMul(),
                "rewardMul", adj.rewardMul(),
                "variant", variant));
        return adj;
    }

    public Map<String, Object> snapshot(long playerId) {
        DifficultyEvaluator.Adjustment adj = evaluate(playerId);
        RunningStats s = load(playerId);
        Map<String, Object> tiered = BossStrategyTier.merge(adj);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("playerId", playerId);
        out.put("samples", s.samples);
        out.put("skillFrequencyMul", adj.skillFrequencyMul());
        out.put("mobCountMul", adj.mobCountMul());
        out.put("rewardMul", adj.rewardMul());
        out.put("overallDifficulty", adj.overallDifficulty());
        out.put("reason", adj.reason());
        out.put("weights", adj.weights());
        out.put("experiment", ExperimentAssigner.assign(playerId, "dda_algo", "baseline", "aggressive"));
        out.put("strategyTier", tiered);
        return out;
    }

    private RunningStats load(long playerId) {
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get(REDIS_KEY + playerId);
                if (raw != null && !raw.isBlank()) {
                    String[] p = raw.split(",");
                    if (p.length >= 4) {
                        RunningStats s = new RunningStats();
                        s.samples = Integer.parseInt(p[0]);
                        s.dpsSum = Double.parseDouble(p[1]);
                        s.surviveCount = Integer.parseInt(p[2]);
                        s.clearTimeSumMs = Long.parseLong(p[3]);
                        return s;
                    }
                }
            } catch (Exception e) {
                log.warn("DDA Redis 读取失败 playerId={}", playerId, e);
            }
        }
        return local.computeIfAbsent(playerId, id -> new RunningStats());
    }

    private void save(long playerId, RunningStats s) {
        local.put(playerId, s);
        if (redis != null) {
            try {
                String raw = s.samples + "," + s.dpsSum + "," + s.surviveCount + "," + s.clearTimeSumMs;
                redis.opsForValue().set(REDIS_KEY + playerId, raw, TTL);
            } catch (Exception e) {
                log.warn("DDA Redis 写入失败 playerId={}", playerId, e);
            }
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class RunningStats {
        int samples;
        double dpsSum;
        int surviveCount;
        long clearTimeSumMs;
    }
}
