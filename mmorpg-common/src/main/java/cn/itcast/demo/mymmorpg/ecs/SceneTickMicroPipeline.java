package cn.itcast.demo.mymmorpg.ecs;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 微流水线：MovementPipeline(10ms/高频) / LogicPipeline(50ms/中频) / SyncPipeline(100ms/低频)。
 * 密集人群时高频移动不阻塞 Buff/AI 结算与状态同步，保障移动回包 P50 &lt; 20ms。
 */
@Component
public class SceneTickMicroPipeline {

    public enum Pipeline {
        MOVEMENT, LOGIC, SYNC
    }

    public static final long MOVEMENT_BUDGET_MS = 10L;
    public static final long LOGIC_BUDGET_MS = 50L;
    public static final long SYNC_BUDGET_MS = 100L;
    public static final long MOVEMENT_INTERVAL_MS = 10L;
    public static final long LOGIC_INTERVAL_MS = 50L;
    public static final long SYNC_INTERVAL_MS = 100L;

    private final SceneTickEngine movementEngine = new SceneTickEngine();
    private final AtomicLong movementRuns = new AtomicLong();
    private final AtomicLong logicRuns = new AtomicLong();
    private final AtomicLong syncRuns = new AtomicLong();
    private final AtomicLong movementProcessed = new AtomicLong();
    private final AtomicLong logicProcessed = new AtomicLong();
    private final AtomicLong syncProcessed = new AtomicLong();
    private volatile long lastMovementMs;
    private volatile long lastLogicMs;
    private volatile long lastSyncMs;

    public SceneTickMicroPipeline() {
        movementEngine.configure(MOVEMENT_BUDGET_MS);
    }

    /**
     * 高频移动：仅位置/碰撞积分，10ms 预算。
     */
    public Map<String, Object> tickMovement(SceneComponentStore store, long nowMs) {
        if (nowMs - lastMovementMs < MOVEMENT_INTERVAL_MS && lastMovementMs > 0) {
            return Map.of("ok", true, "skipped", true, "pipeline", "MOVEMENT");
        }
        lastMovementMs = nowMs;
        Map<String, Object> result = movementEngine.tick(store, nowMs);
        movementRuns.incrementAndGet();
        movementProcessed.addAndGet(((Number) result.getOrDefault("processed", 0L)).longValue());
        result.put("pipeline", "MOVEMENT");
        result.put("budgetMs", MOVEMENT_BUDGET_MS);
        return result;
    }

    /**
     * 中频逻辑：Buff/AI 结算，50ms 预算，分片处理避免 O(N²) 全量。
     */
    public Map<String, Object> tickLogic(
            Iterable<Long> entityIds,
            BiConsumer<Long, Long> logicHandler,
            long nowMs) {
        if (nowMs - lastLogicMs < LOGIC_INTERVAL_MS && lastLogicMs > 0) {
            return Map.of("ok", true, "skipped", true, "pipeline", "LOGIC");
        }
        lastLogicMs = nowMs;
        long deadline = System.currentTimeMillis() + LOGIC_BUDGET_MS;
        long processed = 0L;
        for (Long entityId : entityIds) {
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            if (entityId != null && entityId > 0) {
                logicHandler.accept(entityId, nowMs);
                processed++;
            }
        }
        logicRuns.incrementAndGet();
        logicProcessed.addAndGet(processed);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("pipeline", "LOGIC");
        body.put("processed", processed);
        body.put("budgetMs", LOGIC_BUDGET_MS);
        return body;
    }

    /**
     * 低频同步：PhysicsStateHash 审计、状态快照，100ms 预算。
     */
    public Map<String, Object> tickSync(
            Iterable<Long> entityIds,
            BiConsumer<Long, Long> syncHandler,
            long nowMs) {
        if (nowMs - lastSyncMs < SYNC_INTERVAL_MS && lastSyncMs > 0) {
            return Map.of("ok", true, "skipped", true, "pipeline", "SYNC");
        }
        lastSyncMs = nowMs;
        long deadline = System.currentTimeMillis() + SYNC_BUDGET_MS;
        long processed = 0L;
        for (Long entityId : entityIds) {
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            if (entityId != null && entityId > 0) {
                syncHandler.accept(entityId, nowMs);
                processed++;
            }
        }
        syncRuns.incrementAndGet();
        syncProcessed.addAndGet(processed);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("pipeline", "SYNC");
        body.put("processed", processed);
        body.put("budgetMs", SYNC_BUDGET_MS);
        return body;
    }

    public SceneTickEngine movementEngine() {
        return movementEngine;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("movementRuns", movementRuns.get());
        m.put("logicRuns", logicRuns.get());
        m.put("syncRuns", syncRuns.get());
        m.put("movementProcessed", movementProcessed.get());
        m.put("logicProcessed", logicProcessed.get());
        m.put("syncProcessed", syncProcessed.get());
        m.put("movementBudgetMs", MOVEMENT_BUDGET_MS);
        m.put("logicBudgetMs", LOGIC_BUDGET_MS);
        m.put("syncBudgetMs", SYNC_BUDGET_MS);
        return m;
    }
}
