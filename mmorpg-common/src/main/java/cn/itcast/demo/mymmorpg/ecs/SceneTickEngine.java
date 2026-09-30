package cn.itcast.demo.mymmorpg.ecs;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 场景 Tick 引擎：不再遍历 Actor 对象，仅处理 Dirty 标记的 ECS 组件 slot。
 * 配合 MoveCmdPool 可在 ZGC 下实现移动 Tick 近乎零分配。
 */
@Component
public class SceneTickEngine {

    private final AtomicLong ticksRun = new AtomicLong();
    private final AtomicLong entitiesProcessed = new AtomicLong();
    private volatile long budgetMs = 5L;

    public void configure(long budgetMs) {
        this.budgetMs = Math.max(1L, budgetMs);
    }

    /**
     * 执行一帧 Tick：仅更新 dirty 实体；超时则本帧停止（下帧继续）。
     */
    public Map<String, Object> tick(SceneComponentStore store, long nowMs) {
        long deadline = System.currentTimeMillis() + budgetMs;
        AtomicLong processed = new AtomicLong();
        store.forEachDirty((slot, entityId, x, y, z, vx, vy, vz, hp) -> {
            if (System.currentTimeMillis() > deadline) {
                return;
            }
            /* 轻量积分：速度推进位置（服务端预测步，不重新标 Dirty） */
            float dt = 0.05f;
            store.applyPositionInTick(entityId, x + vx * dt, y + vy * dt, z + vz * dt);
            processed.incrementAndGet();
        });
        ticksRun.incrementAndGet();
        entitiesProcessed.addAndGet(processed.get());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("processed", processed.get());
        body.put("storeSize", store.size());
        body.put("budgetMs", budgetMs);
        return body;
    }

    public Map<String, Object> stats() {
        return Map.of(
                "ticksRun", ticksRun.get(),
                "entitiesProcessed", entitiesProcessed.get(),
                "budgetMs", budgetMs);
    }
}
