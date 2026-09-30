package cn.itcast.demo.mymmorpg.world.ecosystem;

import cn.itcast.demo.mymmorpg.world.explore.RegionProgressService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生态迁移调度：高探索度区域生物种群迁往相邻区域，形成生态链叙事。
 */
@Service
public class EcoMigrationScheduler {

    public record MigrationPlan(
            String creatureUid,
            String fromRegionId,
            String toRegionId,
            float destX, float destY, float destZ,
            long scheduledAtMs) {
    }

    private static final int EXPLORATION_THRESHOLD = 80;

    private final RegionProgressService regionProgress;
    private final EcosystemBehaviorService ecosystem;
    private final ConcurrentHashMap<String, List<MigrationPlan>> pending = new ConcurrentHashMap<>();

    public EcoMigrationScheduler() {
        this(new RegionProgressService(), new EcosystemBehaviorService());
    }

    public EcoMigrationScheduler(RegionProgressService regionProgress, EcosystemBehaviorService ecosystem) {
        this.regionProgress = regionProgress == null ? new RegionProgressService() : regionProgress;
        this.ecosystem = ecosystem == null ? new EcosystemBehaviorService() : ecosystem;
    }

    public Map<String, Object> evaluateAndSchedule(
            long playerId, String clearedRegionId, String adjacentRegionId, long nowMs) {
        int percent = explorationPercent(playerId, clearedRegionId);
        if (percent < EXPLORATION_THRESHOLD) {
            return Map.of("ok", false, "reason", "exploration_below_threshold", "percent", percent);
        }
        List<MigrationPlan> plans = new ArrayList<>();
        plans.add(new MigrationPlan(
                "eco-fox-1", clearedRegionId, adjacentRegionId,
                120f, 0f, 80f, nowMs + 300_000L));
        plans.add(new MigrationPlan(
                "eco-boar-1", clearedRegionId, adjacentRegionId,
                150f, 0f, 90f, nowMs + 600_000L));
        pending.put(clearedRegionId, plans);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("clearedRegionId", clearedRegionId);
        body.put("adjacentRegionId", adjacentRegionId);
        body.put("explorationPercent", percent);
        body.put("migrationsScheduled", plans.size());
        body.put("narrative", "清理区域后，动物种群开始向相邻荒野迁移");
        return body;
    }

    public Map<String, Object> tickExecute(long nowMs) {
        List<Map<String, Object>> executed = new ArrayList<>();
        for (var entry : pending.entrySet()) {
            List<MigrationPlan> plans = entry.getValue();
            plans.removeIf(plan -> {
                if (plan.scheduledAtMs() <= nowMs) {
                    ecosystem.tick(plan.creatureUid(), 12, nowMs, false);
                    executed.add(Map.of(
                            "creatureUid", plan.creatureUid(),
                            "from", plan.fromRegionId(),
                            "to", plan.toRegionId(),
                            "dest", Map.of("x", plan.destX(), "y", plan.destY(), "z", plan.destZ())));
                    return true;
                }
                return false;
            });
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("executedCount", executed.size());
        body.put("migrations", executed);
        return body;
    }

    private int explorationPercent(long playerId, String regionId) {
        Map<String, Object> status = regionProgress.status(playerId, regionId);
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            return 0;
        }
        Object rp = status.get("region_progress");
        if (rp instanceof Map<?, ?> m && m.get("percent") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }
}
