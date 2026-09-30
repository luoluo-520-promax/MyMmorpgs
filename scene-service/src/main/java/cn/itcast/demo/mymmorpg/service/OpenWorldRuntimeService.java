package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.aoi.AoiOctree;
import cn.itcast.demo.mymmorpg.aoi.OcclusionCuller;
import cn.itcast.demo.mymmorpg.gm.GmCommandDispatcher;
import cn.itcast.demo.mymmorpg.metrics.BusinessMetricsDashboard;
import cn.itcast.demo.mymmorpg.metrics.PerformanceHardeningMetrics;
import cn.itcast.demo.mymmorpg.persistence.PlayerWritePipeline;
import cn.itcast.demo.mymmorpg.world.boss.BossRespawnTimer;
import cn.itcast.demo.mymmorpg.world.level.WorldLevelManager;
import cn.itcast.demo.mymmorpg.world.lock.DistributedEntityLock;
import cn.itcast.demo.mymmorpg.world.lock.PartyEntityOwnership;
import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipBitmapStore;
import cn.itcast.demo.mymmorpg.world.loot.LootOwnershipPolicy;
import cn.itcast.demo.mymmorpg.world.migrate.GatewayRoutingTable;
import cn.itcast.demo.mymmorpg.world.migrate.PlayerProxyHandshake;
import cn.itcast.demo.mymmorpg.world.npc.NpcBehaviorFsm;
import cn.itcast.demo.mymmorpg.world.portal.PortalConfig;
import cn.itcast.demo.mymmorpg.world.portal.PortalPreloadService;
import cn.itcast.demo.mymmorpg.world.reconnect.ReconnectProtectionService;
import cn.itcast.demo.mymmorpg.world.resource.RespawnPoint;
import cn.itcast.demo.mymmorpg.world.resource.WorldResourceService;
import cn.itcast.demo.mymmorpg.world.gameplay.OpenWorldGameplayFacade;
import cn.itcast.demo.mymmorpg.world.state.HostWorldContext;
import cn.itcast.demo.mymmorpg.world.state.WorldStateService;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大世界运行时门面：WorldState / WorldLevel / Portal 预加载 / Boss 刷新锁 / 联机物权 / 业务指标。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service")
public class OpenWorldRuntimeService {

    private final WorldResourceService worldResourceService;
    private final WorldTimeService worldTimeService;
    private final WorldStateService worldStateService;
    private final WorldLevelManager worldLevelManager;
    private final BossRespawnTimer bossRespawnTimer;
    private final PortalPreloadService portalPreloadService;
    private final PartyEntityOwnership partyEntityOwnership;
    private final ReconnectProtectionService reconnectProtection;
    private final BusinessMetricsDashboard businessMetrics;
    private final NpcBehaviorFsm npcBehaviorFsm = new NpcBehaviorFsm();
    private final GatewayRoutingTable routingTable;
    private final PlayerProxyHandshake playerProxyHandshake;
    private final DistributedEntityLock entityLock;
    private final GmCommandDispatcher gmDispatcher = new GmCommandDispatcher();
    private final AtomicLong weatherBroadcastCount = new AtomicLong();
    private final LootOwnershipBitmapStore lootBitmapStore;
    private final OpenWorldGameplayFacade gameplay;
    private final PlayerWritePipeline writePipeline;
    private final PerformanceHardeningMetrics perfMetrics;
    private volatile String localNodeId = "scene-local";
    private volatile String lastBroadcastWeather = WorldTimeService.Weather.CLEAR.name();

    public OpenWorldRuntimeService(
            ObjectProvider<WorldResourceService> worldResourceService,
            ObjectProvider<WorldTimeService> worldTimeService,
            ObjectProvider<WorldStateService> worldStateService,
            ObjectProvider<WorldLevelManager> worldLevelManager,
            ObjectProvider<BossRespawnTimer> bossRespawnTimer,
            ObjectProvider<PortalPreloadService> portalPreloadService,
            ObjectProvider<PartyEntityOwnership> partyEntityOwnership,
            ObjectProvider<GatewayRoutingTable> routingTable,
            ObjectProvider<PlayerProxyHandshake> playerProxyHandshake,
            ObjectProvider<DistributedEntityLock> entityLock,
            ObjectProvider<LootOwnershipBitmapStore> lootBitmapStore,
            ObjectProvider<OpenWorldGameplayFacade> gameplayFacade,
            ObjectProvider<PlayerWritePipeline> writePipeline,
            ObjectProvider<PerformanceHardeningMetrics> perfMetrics) {
        this.worldResourceService = resolve(worldResourceService, WorldResourceService::new);
        this.worldTimeService = resolve(worldTimeService, WorldTimeService::new);
        this.worldStateService = resolve(worldStateService,
                () -> new WorldStateService(this.worldTimeService, this.worldResourceService));
        this.worldLevelManager = resolve(worldLevelManager, WorldLevelManager::new);
        this.bossRespawnTimer = resolve(bossRespawnTimer, BossRespawnTimer::new);
        this.routingTable = resolve(routingTable, GatewayRoutingTable::new);
        this.playerProxyHandshake = resolve(playerProxyHandshake, PlayerProxyHandshake::new);
        this.entityLock = resolve(entityLock, DistributedEntityLock::new);
        this.partyEntityOwnership = resolve(partyEntityOwnership, () -> new PartyEntityOwnership(this.entityLock));
        this.portalPreloadService = resolve(portalPreloadService,
                () -> new PortalPreloadService(new cn.itcast.demo.mymmorpg.center.MigrationTicketService(),
                        this.routingTable, 15_000L));
        this.lootBitmapStore = resolve(lootBitmapStore, LootOwnershipBitmapStore::new);
        this.gameplay = resolve(gameplayFacade, OpenWorldGameplayFacade::new);
        this.writePipeline = resolve(writePipeline, PlayerWritePipeline::new);
        this.perfMetrics = resolve(perfMetrics, () -> new PerformanceHardeningMetrics(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));
        this.reconnectProtection = new ReconnectProtectionService();
        this.businessMetrics = new BusinessMetricsDashboard();
        this.bossRespawnTimer.configureNode(localNodeId, 15_000L);
        seedDefaults();
        registerGmCommands();
        this.gameplay.tickSlicer().configure(20, 5L);
        this.worldTimeService.onWeatherChange(ev -> {
            lastBroadcastWeather = ev.to().name();
            weatherBroadcastCount.incrementAndGet();
        });
    }

    /** 单元测试便捷构造 */
    public OpenWorldRuntimeService() {
        this(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static <T> T resolve(ObjectProvider<T> provider, java.util.function.Supplier<T> fallback) {
        if (provider == null) {
            return fallback.get();
        }
        T bean = provider.getIfAvailable();
        return bean != null ? bean : fallback.get();
    }

    public void setLocalNodeId(String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            this.localNodeId = nodeId.trim();
            this.bossRespawnTimer.configureNode(this.localNodeId, 15_000L);
        }
    }

    public WorldResourceService resources() {
        return worldResourceService;
    }

    public WorldTimeService time() {
        return worldTimeService;
    }

    public WorldStateService worldState() {
        return worldStateService;
    }

    public WorldLevelManager worldLevel() {
        return worldLevelManager;
    }

    public BossRespawnTimer bossRespawn() {
        return bossRespawnTimer;
    }

    public PortalPreloadService portals() {
        return portalPreloadService;
    }

    public PartyEntityOwnership partyOwnership() {
        return partyEntityOwnership;
    }

    public ReconnectProtectionService reconnectProtection() {
        return reconnectProtection;
    }

    public BusinessMetricsDashboard metrics() {
        return businessMetrics;
    }

    public NpcBehaviorFsm npcs() {
        return npcBehaviorFsm;
    }

    public GatewayRoutingTable routingTable() {
        return routingTable;
    }

    public PlayerProxyHandshake handshake() {
        return playerProxyHandshake;
    }

    public DistributedEntityLock entityLock() {
        return entityLock;
    }

    public LootOwnershipBitmapStore lootBitmap() {
        return lootBitmapStore;
    }

    public OpenWorldGameplayFacade gameplay() {
        return gameplay;
    }

    public GmCommandDispatcher gm() {
        return gmDispatcher;
    }

    @Scheduled(fixedDelayString = "${game.world.tick-ms:1000}")
    public void onWorldTick() {
        long now = System.currentTimeMillis();
        worldStateService.tick(now);
        WorldTimeService.WorldClock clock = worldTimeService.current(now);
        for (var rt : npcBehaviorFsm.snapshot()) {
            long npcId = ((Number) rt.get("npcId")).longValue();
            npcBehaviorFsm.tick(npcId, clock.hourOfDay(), now);
        }
        gameplay.tickGameplay(now);
        if (writePipeline.shouldFlush(now)) {
            writePipeline.flush(ops -> {
                // 演示：生产环境由 JDBC Batch 消费者异步写 MySQL
            });
        }
    }

    /** 玩家资产变更流水线刷盘（默认 1s 或 50 条） */
    @Scheduled(fixedDelayString = "${game.player.write-pipeline-ms:1000}")
    public void flushWritePipeline() {
        writePipeline.flush(ops -> {
            // 演示：生产环境对接 bag/quest JDBC batch
        });
    }

    /** 采集 Bitmap 脏事件批量 Append 落库（默认 5 分钟）；关服前也应调用。 */
    @Scheduled(fixedDelayString = "${game.world.loot-flush-ms:300000}")
    public void flushLootBitmap() {
        var batch = lootBitmapStore.drainDirty(500);
        if (!batch.isEmpty()) {
            // 演示：仅统计；生产可写入 loot_claim_append 表
            businessMetrics.incWorldSharedCollect();
        }
    }

    public Map<String, Object> status() {
        long now = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("nodeId", localNodeId);
        body.put("worldState", worldStateService.snapshot(1, 0L));
        body.put("worldLevel", worldLevelManager.toView(1, 0L));
        body.put("weatherBroadcastCount", weatherBroadcastCount.get());
        body.put("lastWeather", lastBroadcastWeather);
        body.put("npcs", npcBehaviorFsm.snapshot());
        body.put("routes", routingTable.list(1));
        body.put("businessMetrics", businessMetrics.snapshot());
        body.put("time", worldTimeService.toView(now));
        body.put("lootBitmap", lootBitmapStore.stats());
        body.put("gameplay", gameplay.statusOverview());
        body.put("performance", perfMetrics.snapshot());
        return body;
    }

    public Map<String, Object> performanceMetrics() {
        return perfMetrics.snapshot();
    }

    public Map<String, Object> collectWithOwnership(
            String pointId, long playerId, long hostPlayerId, Set<Long> partyIds,
            LootOwnershipPolicy.SyncMode lootMode) {
        LootOwnershipPolicy.LootDecision decision = LootOwnershipPolicy.decide(
                lootMode == null ? LootOwnershipPolicy.SyncMode.WORLD_SHARED : lootMode,
                playerId, hostPlayerId, partyIds);
        if (!decision.allowed()) {
            return Map.of("ok", false, "error", decision.reason(), "loot", LootOwnershipPolicy.toView(decision));
        }
        // 热路径：WORLD_SHARED 采集态仅写 Redis Bitmap，不查 MySQL
        int bit = Math.floorMod(pointId == null ? 0 : pointId.hashCode(), 1_000_000);
        String scope = LootOwnershipBitmapStore.scopeKey(1, 0, "gather");
        if (lootMode == LootOwnershipPolicy.SyncMode.WORLD_SHARED
                || lootMode == null) {
            Map<String, Object> claim = lootBitmapStore.tryClaim(scope, bit, playerId, System.currentTimeMillis());
            if (!Boolean.TRUE.equals(claim.get("ok"))) {
                Map<String, Object> denied = new LinkedHashMap<>(claim);
                denied.put("loot", LootOwnershipPolicy.toView(decision));
                return denied;
            }
        }
        Map<String, Object> collect = worldResourceService.collect(pointId, playerId, System.currentTimeMillis());
        Map<String, Object> body = new LinkedHashMap<>(collect);
        body.put("loot", LootOwnershipPolicy.toView(decision));
        body.put("bitmapScope", scope);
        body.put("bitmapBit", bit);
        if (Boolean.TRUE.equals(collect.get("ok"))
                && "WORLD_SHARED".equals(String.valueOf(collect.get("syncMode")))) {
            businessMetrics.incWorldSharedCollect();
        }
        return body;
    }

    public Map<String, Object> mixedLoadBenchmark(int players, int gatherOps, int combatLocks, int moveQueries) {
        int p = Math.max(1, Math.min(players, 5000));
        int g = Math.max(0, Math.min(gatherOps, 20_000));
        int c = Math.max(0, Math.min(combatLocks, 20_000));
        int m = Math.max(0, Math.min(moveQueries, 50_000));
        AoiOctree octree = new AoiOctree();
        long t0 = System.nanoTime();
        for (int i = 0; i < p; i++) {
            octree.insert(i + 1L, (i % 100) * 10f, (i % 5) * 8f, (i / 100) * 10f);
        }
        long startMove = System.nanoTime();
        for (int i = 0; i < m; i++) {
            float x = (i % 100) * 10f;
            float y = (i % 5) * 8f;
            float z = (i / 100 % 100) * 10f;
            octree.queryIds(x, y, z, 80f);
        }
        long moveMs = (System.nanoTime() - startMove) / 1_000_000L;

        long gatherOk = 0;
        long startGather = System.nanoTime();
        for (int i = 0; i < g; i++) {
            String pointId = "bench-gather-" + (i % Math.max(1, Math.min(p, 200)));
            worldResourceService.registerPoint(new RespawnPoint(
                    pointId, 1, 1, RespawnPoint.RespawnKind.GATHER,
                    0, 0, 0, 1001, 1, false));
            if (Boolean.TRUE.equals(worldResourceService.collect(pointId, i + 1L, System.currentTimeMillis()).get("ok"))) {
                gatherOk++;
            }
        }
        long gatherMs = (System.nanoTime() - startGather) / 1_000_000L;

        long lockOk = 0;
        long startLock = System.nanoTime();
        for (int i = 0; i < c; i++) {
            var handle = entityLock.tryAcquire(1, 9_000_000L + (i % 500), i + 1L, Duration.ofSeconds(2));
            if (handle.acquired()) {
                lockOk++;
                entityLock.release(handle.lockKey(), handle.token());
            }
        }
        long lockMs = (System.nanoTime() - startLock) / 1_000_000L;
        long totalMs = (System.nanoTime() - t0) / 1_000_000L;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("model", "move+gather+combat");
        body.put("players", p);
        body.put("moveQueries", m);
        body.put("moveQueryMs", moveMs);
        body.put("gatherOps", g);
        body.put("gatherOk", gatherOk);
        body.put("gatherMs", gatherMs);
        body.put("combatLocks", c);
        body.put("combatLockOk", lockOk);
        body.put("combatLockMs", lockMs);
        body.put("totalMs", totalMs);
        body.put("note", "single-process mixed load baseline; cluster soak still requires multi-node harness");
        return body;
    }

    public Map<String, Object> aoi3dCompare(int entityCount, int queryCount, float radius) {
        AoiOctree octree = new AoiOctree();
        long octreeMs = octree.benchmarkQueryMillis(entityCount, queryCount, radius);
        var cliff = OcclusionCuller.cliffFilter(6f);
        int filtered = 0;
        for (int i = 0; i < Math.min(queryCount, 200); i++) {
            float[] ray = {0, 0, 0, 10, 20, 10};
            if (!cliff.test(ray)) {
                filtered++;
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("octreeQueryMs", octreeMs);
        body.put("entityCount", entityCount);
        body.put("queryCount", queryCount);
        body.put("cliffFilterSamplesBlocked", filtered);
        return body;
    }

    private void seedDefaults() {
        worldResourceService.registerPoint(new RespawnPoint(
                "gather-mondstadt-1", 1, 1, RespawnPoint.RespawnKind.GATHER,
                120f, 0f, 80f, 2001, 300, false, RespawnPoint.SyncMode.WORLD_SHARED));
        worldResourceService.registerPoint(new RespawnPoint(
                "chest-liyue-1", 1, 2, RespawnPoint.RespawnKind.CHEST,
                800f, 2f, 400f, 3001, 86_400, true, RespawnPoint.SyncMode.WORLD_SHARED));
        worldResourceService.registerPoint(new RespawnPoint(
                "gather-per-player-1", 1, 1, RespawnPoint.RespawnKind.GATHER,
                150f, 0f, 90f, 2002, 300, false, RespawnPoint.SyncMode.PER_PLAYER));
        npcBehaviorFsm.register(new NpcBehaviorFsm.NpcProfile(
                8_000_001L, "杂货商人", true, 8, 22, List.of()));
        npcBehaviorFsm.register(new NpcBehaviorFsm.NpcProfile(
                8_000_002L, "巡逻守卫", false, 0, 0,
                List.of(
                        new NpcBehaviorFsm.PatrolWaypoint(10, 0, 10, 3),
                        new NpcBehaviorFsm.PatrolWaypoint(40, 0, 10, 3),
                        new NpcBehaviorFsm.PatrolWaypoint(40, 0, 40, 3),
                        new NpcBehaviorFsm.PatrolWaypoint(10, 0, 40, 3))));
        long now = System.currentTimeMillis();
        routingTable.upsert(new GatewayRoutingTable.SceneNodeRoute(
                localNodeId, "127.0.0.1", 8082, 1, 1, 0, 7, 0, 7, now));
        routingTable.upsert(new GatewayRoutingTable.SceneNodeRoute(
                "scene-liyue", "127.0.0.1", 8083, 1, 2, 8, 15, 0, 7, now));
        portalPreloadService.register(new PortalConfig(
                "portal-mond-to-liyue", 1, 2, 1,
                790f, 0f, 50f, 8f, 48f,
                10f, 0f, 10f, "scene-liyue"));
        worldLevelManager.setWorldLevel(1, 0L, 3);
        worldStateService.bindHostWorld(new HostWorldContext(
                10001L, 1, 4, 35, List.of(10001L, 10002L), true));
        businessMetrics.setWorldBossAlive(1);
    }

    private void registerGmCommands() {
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "teleport", GmCommandDispatcher.Level.MODERATOR, "场景内瞬移玩家",
                args -> Map.of("ok", true, "action", "teleport",
                        "playerId", args.getOrDefault("playerId", 0),
                        "x", args.getOrDefault("x", 0),
                        "y", args.getOrDefault("y", 0),
                        "z", args.getOrDefault("z", 0))));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "spawn_monster", GmCommandDispatcher.Level.ADMIN, "刷指定怪",
                args -> Map.of("ok", true, "action", "spawn_monster",
                        "templateId", args.getOrDefault("templateId", 0),
                        "x", args.getOrDefault("x", 0),
                        "z", args.getOrDefault("z", 0))));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "reset_world_boss", GmCommandDispatcher.Level.ADMIN, "重置世界BOSS",
                args -> {
                    String bossId = String.valueOf(args.getOrDefault("bossEventId", "world-boss-1"));
                    return bossRespawnTimer.forceReset(bossId);
                }));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "force_weather", GmCommandDispatcher.Level.ADMIN, "强制天气",
                args -> {
                    String w = String.valueOf(args.getOrDefault("weather", "CLEAR"));
                    worldTimeService.forceWeather(WorldTimeService.Weather.valueOf(w));
                    return Map.of("ok", true, "weather", w);
                }));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "reset_resource", GmCommandDispatcher.Level.ADMIN, "重置资源点CD",
                args -> {
                    String pointId = String.valueOf(args.getOrDefault("pointId", ""));
                    worldResourceService.mechanisms().reset(pointId);
                    return Map.of("ok", true, "pointId", pointId);
                }));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "set_world_level", GmCommandDispatcher.Level.ADMIN, "设置世界等级",
                args -> {
                    int worldId = asInt(args.get("worldId"), 1);
                    long hostId = asLong(args.get("hostPlayerId"));
                    int lv = asInt(args.get("worldLevel"), 0);
                    worldLevelManager.setWorldLevel(worldId, hostId, lv);
                    return worldLevelManager.toView(worldId, hostId);
                }));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "world_state_snapshot", GmCommandDispatcher.Level.MODERATOR, "查看世界状态快照",
                args -> worldStateService.snapshot(
                        asInt(args.get("worldId"), 1), asLong(args.get("hostPlayerId")))));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "puzzle_instantiate", GmCommandDispatcher.Level.ADMIN, "JSON 实例化解谜",
                args -> gameplay.puzzles().instantiateFromJson(args)));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "region_enter_chaos", GmCommandDispatcher.Level.ADMIN, "区域进入混沌态",
                args -> gameplay.regions().enterChaos(
                        String.valueOf(args.getOrDefault("regionId", "wolf-camp-valley")),
                        System.currentTimeMillis())));
        gmDispatcher.register(new GmCommandDispatcher.GmCommand(
                "config_reload_cells", GmCommandDispatcher.Level.ADMIN, "热更指定 grid_cell 配置",
                args -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> cells = args.get("gridCells") instanceof java.util.List<?> list
                            ? list.stream().map(String::valueOf).toList()
                            : java.util.List.of(String.valueOf(args.getOrDefault("gridCell", "")));
                    return gameplay.configPatch().reloadCells(cells, System.currentTimeMillis());
                }));
    }

    private static int asInt(Object v, int dft) {
        if (v == null) {
            return dft;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return dft;
        }
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }
}
