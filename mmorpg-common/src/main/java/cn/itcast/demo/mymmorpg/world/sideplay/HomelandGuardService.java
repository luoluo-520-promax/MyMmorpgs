package cn.itcast.demo.mymmorpg.world.sideplay;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 家园受托管控：好友偷取上限、守卫机关、偷取失败减速惩罚。
 */
@Service
public class HomelandGuardService {

    public static final double STEAL_MAX_RATIO = 0.20;
    public static final String DEBUFF_SPEED_DOWN = "DEBUFF_SPEED_DOWN";
    public static final long DEBUFF_MS = 30_000L;

    private final HomelandService homeland;
    private final ConcurrentHashMap<String, Boolean> guards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> homelandCoins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> speedDebuffUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> remainingYield = new ConcurrentHashMap<>();

    public HomelandGuardService() {
        this(new HomelandService());
    }

    public HomelandGuardService(HomelandService homeland) {
        this.homeland = homeland == null ? new HomelandService() : homeland;
    }

    public HomelandService homeland() {
        return homeland;
    }

    public void creditCoins(long playerId, int amount) {
        homelandCoins.merge(playerId, Math.max(0, amount), Integer::sum);
    }

    /** 作物成熟后登记可偷产量（默认 100 单位，偷取最多 20%）。 */
    public Map<String, Object> markReady(String cropId, int totalYield) {
        int y = Math.max(1, totalYield);
        remainingYield.put(cropId, y);
        return Map.of("ok", true, "cropId", cropId, "state", "READY", "totalYield", y,
                "stealCap", (int) Math.floor(y * STEAL_MAX_RATIO));
    }

    public Map<String, Object> installGuard(long ownerId, String plotId, int coinCost) {
        int cost = Math.max(1, coinCost);
        int have = homelandCoins.getOrDefault(ownerId, 0);
        if (have < cost) {
            return Map.of("ok", false, "error", "insufficient_homeland_coin", "need", cost, "have", have);
        }
        homelandCoins.put(ownerId, have - cost);
        guards.put(plotId, true);
        return Map.of("ok", true, "plotId", plotId, "guard", true, "coinCost", cost);
    }

    /**
     * 好友偷取：至多 20% 产量；触发守卫则失败并获得 DEBUFF_SPEED_DOWN 30s。
     */
    public Map<String, Object> steal(long friendId, String plotId, String cropId, long nowMs) {
        Integer remain = remainingYield.get(cropId);
        if (remain == null || remain <= 0) {
            return Map.of("ok", false, "error", "crop_not_ready_or_empty");
        }
        if (Boolean.TRUE.equals(guards.get(plotId))) {
            speedDebuffUntil.put(friendId, nowMs + DEBUFF_MS);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("error", "guard_triggered");
            body.put("stolen", false);
            body.put("debuff", DEBUFF_SPEED_DOWN);
            body.put("debuffMs", DEBUFF_MS);
            body.put("moveSpeedPenalty", 0.5);
            body.put("note", "punitive_fun_in_open_world");
            return body;
        }
        int cap = Math.max(1, (int) Math.floor(remain * STEAL_MAX_RATIO));
        int take = Math.min(cap, remain);
        remainingYield.put(cropId, remain - take);
        List<Map<String, Object>> grantPlan = new ArrayList<>();
        grantPlan.add(Map.of(
                "itemId", "stolen_crop_" + cropId,
                "count", take,
                "source", "homeland_steal",
                "reducedFrom", remain,
                "stealRatioCap", STEAL_MAX_RATIO));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("stolen", true);
        body.put("friendId", friendId);
        body.put("cropId", cropId);
        body.put("amount", take);
        body.put("remainYield", remainingYield.get(cropId));
        body.put("grantPlans", grantPlan);
        body.put("idempotencyKey", "steal:" + friendId + ":" + cropId);
        return body;
    }

    public Map<String, Object> movementPenalty(long playerId, long nowMs) {
        Long until = speedDebuffUntil.get(playerId);
        if (until == null || nowMs >= until) {
            return Map.of("ok", true, "debuffed", false, "speedMul", 1.0);
        }
        return Map.of("ok", true, "debuffed", true, "debuff", DEBUFF_SPEED_DOWN,
                "remainMs", until - nowMs, "speedMul", 0.5);
    }
}
