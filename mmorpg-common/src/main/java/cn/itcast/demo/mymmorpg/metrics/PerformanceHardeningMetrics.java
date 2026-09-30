package cn.itcast.demo.mymmorpg.metrics;

import cn.itcast.demo.mymmorpg.aoi.AoiDeltaEncoder;
import cn.itcast.demo.mymmorpg.cache.LocalPositionCache;
import cn.itcast.demo.mymmorpg.concurrency.BusinessBatchConsumer;
import cn.itcast.demo.mymmorpg.concurrency.SceneActorMailbox;
import cn.itcast.demo.mymmorpg.ecs.SceneTickEngine;
import cn.itcast.demo.mymmorpg.persistence.AssetWalWriter;
import cn.itcast.demo.mymmorpg.persistence.ColdHotDataRouter;
import cn.itcast.demo.mymmorpg.persistence.DeduplicateFilter;
import cn.itcast.demo.mymmorpg.persistence.HistoricalDataRetention;
import cn.itcast.demo.mymmorpg.persistence.PlayerWritePipeline;
import cn.itcast.demo.mymmorpg.physics.AsyncPhysicsThreadPool;
import cn.itcast.demo.mymmorpg.physics.LitePhysicsEngine;
import cn.itcast.demo.mymmorpg.pool.MoveCmdPool;
import cn.itcast.demo.mymmorpg.redis.RedisAtomicScriptService;
import cn.itcast.demo.mymmorpg.support.SimpleBloomFilter;
import cn.itcast.demo.mymmorpg.sync.DynamicFrequencyService;
import cn.itcast.demo.mymmorpg.sync.MoveDeltaEncoder;
import cn.itcast.demo.mymmorpg.world.tick.GameplayTickSlicer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 性能加固指标聚合：并发/AOI/GC/Tick/DB/锁/冷热分离统一观测。
 */
@Component
public class PerformanceHardeningMetrics {

    private final ObjectProvider<SceneActorMailbox> mailbox;
    private final ObjectProvider<BusinessBatchConsumer> batchConsumer;
    private final ObjectProvider<MoveCmdPool> moveCmdPool;
    private final ObjectProvider<AoiDeltaEncoder> deltaEncoder;
    private final ObjectProvider<GameplayTickSlicer> tickSlicer;
    private final ObjectProvider<PlayerWritePipeline> writePipeline;
    private final ObjectProvider<AssetWalWriter> walWriter;
    private final ObjectProvider<RedisAtomicScriptService> atomicScripts;
    private final ObjectProvider<HistoricalDataRetention> retention;
    private final ObjectProvider<SimpleBloomFilter> bloomFilter;
    private final ObjectProvider<SceneTickEngine> sceneTickEngine;
    private final ObjectProvider<DynamicFrequencyService> dynamicFrequency;
    private final ObjectProvider<MoveDeltaEncoder> moveDeltaEncoder;
    private final ObjectProvider<LocalPositionCache> localPositionCache;
    private final ObjectProvider<DeduplicateFilter> dedupeFilter;
    private final ObjectProvider<ColdHotDataRouter> coldHotRouter;
    private final ObjectProvider<AsyncPhysicsThreadPool> asyncPhysics;
    private final ObjectProvider<LitePhysicsEngine> litePhysics;

    public PerformanceHardeningMetrics(
            ObjectProvider<SceneActorMailbox> mailbox,
            ObjectProvider<BusinessBatchConsumer> batchConsumer,
            ObjectProvider<MoveCmdPool> moveCmdPool,
            ObjectProvider<AoiDeltaEncoder> deltaEncoder,
            ObjectProvider<GameplayTickSlicer> tickSlicer,
            ObjectProvider<PlayerWritePipeline> writePipeline,
            ObjectProvider<AssetWalWriter> walWriter,
            ObjectProvider<RedisAtomicScriptService> atomicScripts,
            ObjectProvider<HistoricalDataRetention> retention,
            ObjectProvider<SimpleBloomFilter> bloomFilter,
            ObjectProvider<SceneTickEngine> sceneTickEngine,
            ObjectProvider<DynamicFrequencyService> dynamicFrequency,
            ObjectProvider<MoveDeltaEncoder> moveDeltaEncoder,
            ObjectProvider<LocalPositionCache> localPositionCache,
            ObjectProvider<DeduplicateFilter> dedupeFilter,
            ObjectProvider<ColdHotDataRouter> coldHotRouter,
            ObjectProvider<AsyncPhysicsThreadPool> asyncPhysics,
            ObjectProvider<LitePhysicsEngine> litePhysics) {
        this.mailbox = mailbox;
        this.batchConsumer = batchConsumer;
        this.moveCmdPool = moveCmdPool;
        this.deltaEncoder = deltaEncoder;
        this.tickSlicer = tickSlicer;
        this.writePipeline = writePipeline;
        this.walWriter = walWriter;
        this.atomicScripts = atomicScripts;
        this.retention = retention;
        this.bloomFilter = bloomFilter;
        this.sceneTickEngine = sceneTickEngine;
        this.dynamicFrequency = dynamicFrequency;
        this.moveDeltaEncoder = moveDeltaEncoder;
        this.localPositionCache = localPositionCache;
        this.dedupeFilter = dedupeFilter;
        this.coldHotRouter = coldHotRouter;
        this.asyncPhysics = asyncPhysics;
        this.litePhysics = litePhysics;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("concurrency", Map.of(
                "sceneActorMailbox", statsOf(mailbox),
                "businessBatchConsumer", statsOf(batchConsumer)));
        m.put("gcPooling", Map.of("moveCmdPool", statsOf(moveCmdPool)));
        m.put("aoi", Map.of("deltaEncoder", statsOf(deltaEncoder)));
        m.put("tick", Map.of(
                "gameplayTickSlicer", statsOf(tickSlicer),
                "sceneTickEngine", statsOf(sceneTickEngine)));
        m.put("p15Sync", Map.of(
                "dynamicFrequency", statsOf(dynamicFrequency),
                "moveDeltaEncoder", statsOf(moveDeltaEncoder),
                "localPositionCache", statsOf(localPositionCache)));
        m.put("p15Persistence", Map.of(
                "dedupeFilter", statsOf(dedupeFilter),
                "coldHotRouter", statsOf(coldHotRouter)));
        m.put("p15Physics", Map.of(
                "asyncPhysics", statsOf(asyncPhysics),
                "litePhysics", statsOf(litePhysics)));
        m.put("persistence", Map.of(
                "writePipeline", statsOf(writePipeline),
                "assetWal", statsOf(walWriter),
                "historicalRetention", statsOf(retention)));
        m.put("redisAtomic", Map.of("available", atomicScripts != null && atomicScripts.getIfAvailable() != null));
        m.put("bloomFilter", statsOf(bloomFilter));
        return m;
    }

    private static Map<String, Object> statsOf(ObjectProvider<?> provider) {
        Object bean = provider == null ? null : provider.getIfAvailable();
        if (bean == null) {
            return Map.of("available", false);
        }
        try {
            var method = bean.getClass().getMethod("stats");
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) method.invoke(bean);
            Map<String, Object> body = new LinkedHashMap<>(stats);
            body.put("available", true);
            return body;
        } catch (Exception e) {
            return Map.of("available", true, "error", e.getMessage());
        }
    }
}
