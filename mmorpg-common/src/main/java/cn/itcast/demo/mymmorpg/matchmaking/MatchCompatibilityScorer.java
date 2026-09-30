package cn.itcast.demo.mymmorpg.matchmaking;

/**
 * 匹配互补评分：等级/战力接近且等待越久得分越高；可选胜率平衡、职业互补、风格偏好。
 */
public final class MatchCompatibilityScorer {

    public record Profile(
            int level,
            int power,
            long enqueueTimeMs,
            String role,
            double winRate,
            String stylePreference) {
    }

    private MatchCompatibilityScorer() {
    }

    /**
     * 分数越高越适合组队；同玩家或空入参返回负无穷。
     */
    public static double score(int levelA, int powerA, long enqueueTimeA,
                               int levelB, int powerB, long enqueueTimeB) {
        return score(new Profile(levelA, powerA, enqueueTimeA, null, -1, null),
                new Profile(levelB, powerB, enqueueTimeB, null, -1, null));
    }

    /**
     * 多目标启发式：基础分 + 胜率接近奖励 + 职业互补 + 风格偏好一致。
     */
    public static double score(Profile a, Profile b) {
        if (a == null || b == null || a.level() <= 0 || b.level() <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        int levelDiff = Math.abs(a.level() - b.level());
        int powerDiff = Math.abs(a.power() - b.power());
        long earliest = Math.min(a.enqueueTimeMs(), b.enqueueTimeMs());
        if (earliest <= 0L) {
            earliest = System.currentTimeMillis();
        }
        double waitBonus = Math.min(30.0, (System.currentTimeMillis() - earliest) / 1000.0);
        double base = 100.0 - levelDiff * 4.0 - Math.min(50.0, powerDiff / 100.0) + waitBonus * 0.1;

        double winBonus = 0.0;
        if (a.winRate() >= 0 && b.winRate() >= 0) {
            double wrDiff = Math.abs(a.winRate() - b.winRate());
            winBonus = 8.0 * (1.0 - Math.min(1.0, wrDiff / 0.35));
        }

        double roleBonus = roleComplement(a.role(), b.role());
        double styleBonus = 0.0;
        if (a.stylePreference() != null && a.stylePreference().equalsIgnoreCase(b.stylePreference())) {
            styleBonus = 3.0;
        }
        return base + winBonus + roleBonus + styleBonus;
    }

    private static double roleComplement(String roleA, String roleB) {
        if (roleA == null || roleB == null || roleA.isBlank() || roleB.isBlank()) {
            return 0.0;
        }
        String a = roleA.toLowerCase();
        String b = roleB.toLowerCase();
        if (a.equals(b)) {
            return -2.0; // 同职业略罚，鼓励多样性
        }
        boolean hasTank = a.contains("tank") || b.contains("tank") || a.contains("坦") || b.contains("坦");
        boolean hasHeal = a.contains("heal") || b.contains("heal") || a.contains("奶") || b.contains("奶");
        boolean hasDps = a.contains("dps") || b.contains("dps") || a.contains("输出") || b.contains("输出");
        if (hasTank && hasHeal) {
            return 10.0;
        }
        if ((hasTank || hasHeal) && hasDps) {
            return 6.0;
        }
        return 2.0;
    }
}
