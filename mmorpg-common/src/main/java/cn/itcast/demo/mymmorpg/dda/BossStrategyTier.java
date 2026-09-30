package cn.itcast.demo.mymmorpg.dda;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Boss 策略档位：离线 RL/搜索产出的参数集，线上按玩家水平切换（含上下限）。
 */
public final class BossStrategyTier {

    public record TierParams(
            String tier,
            double skillCdMul,
            double damageMul,
            double summonRateMul,
            double hpMul) {

        public TierParams {
            skillCdMul = clamp(skillCdMul, 0.7, 1.5);
            damageMul = clamp(damageMul, 0.75, 1.4);
            summonRateMul = clamp(summonRateMul, 0.7, 1.5);
            hpMul = clamp(hpMul, 0.8, 1.35);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tier", tier);
            m.put("skillCdMul", skillCdMul);
            m.put("damageMul", damageMul);
            m.put("summonRateMul", summonRateMul);
            m.put("hpMul", hpMul);
            return m;
        }
    }

    public static final TierParams NOVICE = new TierParams("NOVICE", 1.25, 0.85, 0.8, 0.9);
    public static final TierParams STANDARD = new TierParams("STANDARD", 1.0, 1.0, 1.0, 1.0);
    public static final TierParams VETERAN = new TierParams("VETERAN", 0.9, 1.12, 1.15, 1.1);
    public static final TierParams ELITE = new TierParams("ELITE", 0.8, 1.25, 1.3, 1.2);

    /**
     * 由 DDA overallDifficulty 映射档位。
     */
    public static TierParams fromDifficulty(double overallDifficulty) {
        double d = overallDifficulty;
        if (d < 0.85) {
            return NOVICE;
        }
        if (d < 1.05) {
            return STANDARD;
        }
        if (d < 1.25) {
            return VETERAN;
        }
        return ELITE;
    }

    /**
     * 合并 DDA 调整与档位参数，并再次夹紧上下限。
     */
    public static Map<String, Object> merge(DifficultyEvaluator.Adjustment adj) {
        TierParams tier = fromDifficulty(adj == null ? 1.0 : adj.overallDifficulty());
        double skillFreq = adj == null ? 1.0 : adj.skillFrequencyMul();
        double mob = adj == null ? 1.0 : adj.mobCountMul();
        Map<String, Object> out = new LinkedHashMap<>(tier.toMap());
        out.put("skillFrequencyMul", clamp(skillFreq / tier.skillCdMul(), 0.7, 1.6));
        out.put("mobCountMul", clamp(mob * tier.summonRateMul(), 0.7, 1.5));
        out.put("rewardMul", adj == null ? 1.0 : adj.rewardMul());
        out.put("overallDifficulty", adj == null ? 1.0 : adj.overallDifficulty());
        out.put("ddaReason", adj == null ? "none" : adj.reason());
        out.put("source", "dda+strategy_tier");
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
