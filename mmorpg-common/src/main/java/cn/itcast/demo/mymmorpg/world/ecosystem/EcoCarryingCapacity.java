package cn.itcast.demo.mymmorpg.world.ecosystem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 种群密度动态调控：击杀过多某类生物 → 刷新率下降，并触发生态失衡 Debuff（作物减产）。
 */
public final class EcoCarryingCapacity {

    public static final int OVERKILL_THRESHOLD = 20;
    public static final double MIN_SPAWN_RATE = 0.25;
    public static final String DEBUFF_ECO_IMBALANCE = "ECO_IMBALANCE";

    private final ConcurrentHashMap<String, Integer> kills = new ConcurrentHashMap<>();

    public void recordKill(String regionId, String species) {
        kills.merge(bucket(regionId, species), 1, Integer::sum);
    }

    public int kills(String regionId, String species) {
        return kills.getOrDefault(bucket(regionId, species), 0);
    }

    /** 刷新倍率：0.25~1.0；达过杀阈值即开始下降。 */
    public double spawnRateMultiplier(String regionId, String species) {
        int k = kills(regionId, species);
        if (k < OVERKILL_THRESHOLD) {
            return 1.0;
        }
        double over = (k - OVERKILL_THRESHOLD + 1) / 40.0;
        return Math.max(MIN_SPAWN_RATE, 1.0 - over);
    }

    public boolean imbalanced(String regionId, String species) {
        return kills(regionId, species) >= OVERKILL_THRESHOLD;
    }

    /** 作物产量倍率：失衡区域减产。 */
    public double cropYieldMultiplier(String regionId) {
        for (Map.Entry<String, Integer> e : kills.entrySet()) {
            if (e.getKey().startsWith((regionId == null ? "" : regionId.trim()) + ":")
                    && e.getValue() >= OVERKILL_THRESHOLD) {
                return 0.5;
            }
        }
        return 1.0;
    }

    public Map<String, Object> snapshot(String regionId, String species) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("regionId", regionId);
        body.put("species", species);
        body.put("kills", kills(regionId, species));
        body.put("spawnRate", spawnRateMultiplier(regionId, species));
        body.put("imbalanced", imbalanced(regionId, species));
        body.put("cropYield", cropYieldMultiplier(regionId));
        if (imbalanced(regionId, species)) {
            body.put("regionDebuff", DEBUFF_ECO_IMBALANCE);
        }
        return body;
    }

    public Map<String, Object> applyToPlacementTarget(String regionId, String species, int baseTarget) {
        double rate = spawnRateMultiplier(regionId, species);
        int adjusted = Math.max(1, (int) Math.round(baseTarget * rate));
        Map<String, Object> body = new LinkedHashMap<>(snapshot(regionId, species));
        body.put("ok", true);
        body.put("baseTarget", baseTarget);
        body.put("adjustedTarget", adjusted);
        return body;
    }

    private static String bucket(String regionId, String species) {
        return (regionId == null ? "" : regionId.trim()) + ":"
                + (species == null ? "" : species.trim());
    }
}
