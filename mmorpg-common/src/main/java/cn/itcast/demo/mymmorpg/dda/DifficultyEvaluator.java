package cn.itcast.demo.mymmorpg.dda;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态难度修正系数：基于 DPS、生存率、通关时间计算 Boss 技能频率 / 小怪数量 / 奖励倍率。
 */
public final class DifficultyEvaluator {

    public record BattleStats(
            double dps,
            double survivalRate,
            long clearTimeMs,
            long expectedClearTimeMs,
            double expectedDps) {
    }

    public record Adjustment(
            double skillFrequencyMul,
            double mobCountMul,
            double rewardMul,
            double overallDifficulty,
            String reason,
            Map<String, Double> weights) {
    }

    /**
     * @param stats 近期战斗统计；非法输入回退到中性修正。
     */
    public Adjustment evaluate(BattleStats stats) {
        if (stats == null) {
            return neutral("no_stats");
        }
        double expectedDps = Math.max(1.0, stats.expectedDps());
        double dpsRatio = clamp(stats.dps() / expectedDps, 0.3, 2.5);
        double survival = clamp(stats.survivalRate(), 0.0, 1.0);
        long expectedClear = Math.max(1_000L, stats.expectedClearTimeMs());
        double clearRatio = clamp((double) stats.clearTimeMs() / expectedClear, 0.3, 3.0);

        // 玩家过强 → 提高难度；过弱 → 降低难度并略增奖励
        double pressure = 0.45 * dpsRatio + 0.30 * survival + 0.25 * (2.0 - Math.min(2.0, clearRatio));
        double difficulty = clamp(0.7 + (pressure - 1.0) * 0.55, 0.6, 1.5);

        double skillMul = clamp(0.75 + (difficulty - 0.7) * 0.9, 0.7, 1.6);
        double mobMul = clamp(0.8 + (difficulty - 0.7) * 0.7, 0.7, 1.4);
        double rewardMul = clamp(1.15 - (difficulty - 1.0) * 0.35, 0.85, 1.35);

        String reason;
        if (difficulty >= 1.05) {
            reason = "player_overpowered";
        } else if (difficulty <= 0.9) {
            reason = "player_struggling";
        } else {
            reason = "balanced";
        }

        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("dpsRatio", dpsRatio);
        weights.put("survival", survival);
        weights.put("clearRatio", clearRatio);
        weights.put("pressure", pressure);

        return new Adjustment(round2(skillMul), round2(mobMul), round2(rewardMul), round2(difficulty), reason, weights);
    }

    private static Adjustment neutral(String reason) {
        return new Adjustment(1.0, 1.0, 1.0, 1.0, reason, Map.of());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
