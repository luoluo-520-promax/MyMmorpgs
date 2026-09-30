package cn.itcast.demo.mymmorpg.experiment;

/**
 * 稳定分桶 A/B：同一 playerId + experimentKey 始终落入同一变体。
 */
public final class ExperimentAssigner {

    private ExperimentAssigner() {
    }

    /**
     * @param variants 变体列表，至少 1 个；按顺序均分桶。
     */
    public static String assign(long playerId, String experimentKey, String... variants) {
        if (variants == null || variants.length == 0) {
            return "control";
        }
        if (variants.length == 1) {
            return variants[0];
        }
        int h = mix(playerId, experimentKey == null ? "" : experimentKey);
        int idx = Math.floorMod(h, variants.length);
        return variants[idx];
    }

    public static boolean inTreatment(long playerId, String experimentKey, int treatmentPercent) {
        int pct = Math.max(0, Math.min(100, treatmentPercent));
        if (pct <= 0) {
            return false;
        }
        if (pct >= 100) {
            return true;
        }
        int h = Math.floorMod(mix(playerId, experimentKey == null ? "" : experimentKey), 100);
        return h < pct;
    }

    private static int mix(long playerId, String key) {
        int h = Long.hashCode(playerId);
        h = 31 * h + key.hashCode();
        h ^= (h >>> 16);
        return h;
    }
}
