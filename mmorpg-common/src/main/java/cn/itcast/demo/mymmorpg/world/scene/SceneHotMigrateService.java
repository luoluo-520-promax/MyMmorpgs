package cn.itcast.demo.mymmorpg.world.scene;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 场景 Zone 热迁移：维护时将玩家批量签发无缝交接票据，目标节点接管后原节点进入 DRAINING。
 */
@Component
public class SceneHotMigrateService {

    public enum MigrateStatus { PENDING, TICKETS_ISSUED, DRAINING, COMPLETED, FAILED }

    public record EntitySnapshot(
            long entityId,
            int entityType,
            float x, float y, float z,
            String name,
            int level) {
    }

    public record MigratePlan(
            String planId,
            int sceneId,
            int zoneId,
            String fromNodeId,
            String toNodeId,
            String toHost,
            int toPort,
            List<Long> playerIds,
            List<EntitySnapshot> monsters,
            Map<Long, String> playerTickets,
            MigrateStatus status,
            long createdAtMs) {
    }

    private final MigrationTicketService tickets;
    private final ConcurrentHashMap<String, MigratePlan> plans = new ConcurrentHashMap<>();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public SceneHotMigrateService() {
        this(new MigrationTicketService());
    }

    public SceneHotMigrateService(MigrationTicketService tickets) {
        this.tickets = tickets == null ? new MigrationTicketService() : tickets;
    }

    /**
     * 为 Zone 内玩家批量签发 seamless 票据，供目标 Scene 节点接管。
     */
    public MigratePlan beginZoneMigrate(
            int sceneId,
            int zoneId,
            int lineId,
            String fromNodeId,
            String toNodeId,
            String toHost,
            int toPort,
            List<Long> playerIds,
            Map<Long, float[]> playerPositions,
            List<EntitySnapshot> monsters,
            long nowMs) {
        String planId = "mig-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<Long, String> ticketMap = new LinkedHashMap<>();
        List<Long> players = playerIds == null ? List.of() : List.copyOf(playerIds);
        for (Long pid : players) {
            float[] pos = playerPositions == null ? null : playerPositions.get(pid);
            float x = pos == null ? 0f : pos[0];
            float y = pos == null || pos.length < 2 ? 0f : pos[1];
            float z = pos == null || pos.length < 3 ? 0f : pos[2];
            String ticket = tickets.issueSeamless(
                    pid, sceneId, lineId, 0, x, y, z, 0f, 0f, 0f, zoneId);
            ticketMap.put(pid, ticket);
        }
        MigratePlan plan = new MigratePlan(
                planId, sceneId, zoneId,
                fromNodeId == null ? "" : fromNodeId,
                toNodeId == null ? "" : toNodeId,
                toHost == null ? "127.0.0.1" : toHost,
                toPort <= 0 ? 8082 : toPort,
                players,
                monsters == null ? List.of() : List.copyOf(monsters),
                Map.copyOf(ticketMap),
                MigrateStatus.TICKETS_ISSUED,
                nowMs);
        plans.put(planId, plan);
        return plan;
    }

    public MigratePlan markDraining(String planId) {
        MigratePlan cur = plans.get(planId);
        if (cur == null) {
            return null;
        }
        MigratePlan next = withStatus(cur, MigrateStatus.DRAINING);
        plans.put(planId, next);
        return next;
    }

    public MigratePlan complete(String planId) {
        MigratePlan cur = plans.get(planId);
        if (cur == null) {
            return null;
        }
        MigratePlan next = withStatus(cur, MigrateStatus.COMPLETED);
        plans.put(planId, next);
        completed.incrementAndGet();
        return next;
    }

    public MigratePlan fail(String planId) {
        MigratePlan cur = plans.get(planId);
        if (cur == null) {
            return null;
        }
        MigratePlan next = withStatus(cur, MigrateStatus.FAILED);
        plans.put(planId, next);
        failed.incrementAndGet();
        return next;
    }

    public MigratePlan get(String planId) {
        return plans.get(planId);
    }

    public Map<String, Object> toView(MigratePlan plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("planId", plan.planId());
        m.put("sceneId", plan.sceneId());
        m.put("zoneId", plan.zoneId());
        m.put("fromNodeId", plan.fromNodeId());
        m.put("toNodeId", plan.toNodeId());
        m.put("toHost", plan.toHost());
        m.put("toPort", plan.toPort());
        m.put("playerCount", plan.playerIds().size());
        m.put("monsterCount", plan.monsters().size());
        m.put("tickets", plan.playerTickets());
        m.put("status", plan.status().name());
        m.put("createdAtMs", plan.createdAtMs());
        return m;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trackedPlans", plans.size());
        m.put("completed", completed.get());
        m.put("failed", failed.get());
        return m;
    }

    public List<MigratePlan> listActive() {
        List<MigratePlan> out = new ArrayList<>();
        for (MigratePlan p : plans.values()) {
            if (p.status() == MigrateStatus.TICKETS_ISSUED || p.status() == MigrateStatus.DRAINING) {
                out.add(p);
            }
        }
        return out;
    }

    private static MigratePlan withStatus(MigratePlan cur, MigrateStatus status) {
        return new MigratePlan(
                cur.planId(), cur.sceneId(), cur.zoneId(), cur.fromNodeId(), cur.toNodeId(),
                cur.toHost(), cur.toPort(), cur.playerIds(), cur.monsters(), cur.playerTickets(),
                status, cur.createdAtMs());
    }
}
