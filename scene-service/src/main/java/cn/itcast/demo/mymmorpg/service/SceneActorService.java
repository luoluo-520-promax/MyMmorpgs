/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/SceneActorService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：大世界场景运行时——分线实例、实体 AOI、移动同步、刷怪与战斗前置校验。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 大世界场景运行时——分线实例、实体 AOI、移动同步、刷怪与战斗前置校验

import cn.itcast.demo.mymmorpg.entity.MapConfig; // map_config 表：场景 ID、宽高、默认分线数
import cn.itcast.demo.mymmorpg.entity.MonsterConfig; // monster_config 表：刷怪模板（名称、等级、modelId）
import cn.itcast.demo.mymmorpg.entity.Player; // 玩家实体，进场景时取 name/level 组装 EntityInfo
import cn.itcast.demo.mymmorpg.model.MonsterWaveSimpleFactory; // 将怪物模板分组为波次，决定刷怪坐标种子
import cn.itcast.demo.mymmorpg.protocol.MessageId; // ENTER_SCENE_SC_RSP、SYNC_ENTITY_SC_NOTIFY 等消息号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // Handler 统一返回 msgId + Protobuf payload
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 场景/移动/分线等业务 retcode
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneCsReq; // 进场景 CsReq（sceneId、lineId、出生点）
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneScRsp; // 进场景 ScRsp（坐标 + 可见实体列表）
import cn.itcast.demo.mymmorpg.protocol.protobuf.EntityInfo; // 场景实体 Protobuf（id、类型、坐标、模型）
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetCurSceneInfoCsReq; // 查询当前场景 CsReq
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetCurSceneInfoScRsp; // 当前场景 ScRsp
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetNearbyEntitiesCsReq; // AOI 范围查询 CsReq（圆心+半径）
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetNearbyEntitiesScRsp; // 范围内实体 ScRsp
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveCsReq; // 移动 CsReq（目标坐标、速度、客户端时间戳）
import cn.itcast.demo.mymmorpg.protocol.protobuf.MoveScRsp; // 移动 ScRsp（服务端确认坐标）
import cn.itcast.demo.mymmorpg.protocol.protobuf.SyncEntityScNotify; // 服务端主动推送：实体进入/离开/移动
import cn.itcast.demo.mymmorpg.protocol.protobuf.SwitchLineCsReq; // 切换分线 CsReq（targetLineId）
import cn.itcast.demo.mymmorpg.protocol.protobuf.SwitchLineScRsp; // 切换分线 ScRsp
import cn.itcast.demo.mymmorpg.protocol.protobuf.TransferSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.TransferSceneScRsp;
import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.service.ConfigQueryService;
import cn.itcast.demo.mymmorpg.service.SceneEventPublisher;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import cn.itcast.demo.mymmorpg.anticheat.AntiCheatService;
import cn.itcast.demo.mymmorpg.aoi.AoiBroadcastStrategy;
import cn.itcast.demo.mymmorpg.aoi.AoiDeltaEncoder;
import cn.itcast.demo.mymmorpg.aoi.AoiGrid;
import cn.itcast.demo.mymmorpg.aoi.AoiOctree;
import cn.itcast.demo.mymmorpg.aoi.AoiUpdateBatcher;
import cn.itcast.demo.mymmorpg.aoi.OcclusionCuller;
import cn.itcast.demo.mymmorpg.aoi.PrimitiveGridStore;
import cn.itcast.demo.mymmorpg.aoi.VisualSignificanceScheduler;
import cn.itcast.demo.mymmorpg.cache.LocalPositionCache;
import cn.itcast.demo.mymmorpg.cache.RedisPositionBatchWriter;
import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.ecs.SceneComponentStore;
import cn.itcast.demo.mymmorpg.ecs.SceneTickEngine;
import cn.itcast.demo.mymmorpg.ecs.SceneTickMicroPipeline;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ResumeSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ResumeSceneScRsp;
import cn.itcast.demo.mymmorpg.service.CenterSceneRouter;
import cn.itcast.demo.mymmorpg.pool.MoveCmdPool;
import cn.itcast.demo.mymmorpg.sync.DynamicFrequencyService;
import cn.itcast.demo.mymmorpg.sync.BroadcastImportanceFuseService;
import cn.itcast.demo.mymmorpg.sync.MergedMoveAckService;
import cn.itcast.demo.mymmorpg.sync.MoveDeltaEncoder;
import cn.itcast.demo.mymmorpg.sync.ActionState;
import cn.itcast.demo.mymmorpg.sync.MoveIntent;
import cn.itcast.demo.mymmorpg.sync.MovementType;
import cn.itcast.demo.mymmorpg.sync.MovementPredictionValidator;
import cn.itcast.demo.mymmorpg.sync.NavMeshPathValidator;
import cn.itcast.demo.mymmorpg.sync.SceneMoveCmd;
import cn.itcast.demo.mymmorpg.sync.SnapshotBuffer;
import cn.itcast.demo.mymmorpg.world.WorldZoneManager;
import cn.itcast.demo.mymmorpg.world.ZoneLoadBalancer;
import cn.itcast.demo.mymmorpg.world.debug.EntityDebugTrace;
import cn.itcast.demo.mymmorpg.world.level.WorldLevelManager;
import cn.itcast.demo.mymmorpg.world.line.LineShardScheduler;
import cn.itcast.demo.mymmorpg.world.lock.DistributedEntityLock;
import cn.itcast.demo.mymmorpg.world.reconnect.ReconnectProtectionService;
import cn.itcast.demo.mymmorpg.world.scene.SceneHotMigrateService;
import cn.itcast.demo.mymmorpg.world.content.SceneConfigResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList; // AOI 命中实体、进场景可见列表等可变 Protobuf 组装
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List; // enterRsp/switchRsp 的 EntityInfo 列表参数
import java.util.Map;
import java.util.Optional; // 怪物战斗引用、施法距离等可能无结果的查询
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap; // lineStates/playerScene 分线实体表并发读写
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong; // 怪物 entityId 全局自增，与玩家 ID 空间分离
import java.util.function.Predicate;

/**
 * 场景 Actor 运行时：内存维护「地图:分线 -> 实体表」，地图/怪物配置来自 MySQL + ConfigQueryService 缓存。
 * 实现 BattleScenePort，供 SkillService 施法距离校验、BattleService 开战前校验怪物实体。
 */
@Service // 内存维护「地图:分线→实体表」，实现 BattleScenePort 供战斗/技能模块查怪物与距离
@EnableConfigurationProperties(SceneRuntimeProperties.class)
public class SceneActorService implements BattleScenePort { // 对外暴露场景查询端口供战斗/技能模块调用

    /** 实体类型：1=玩家，客户端据此渲染玩家模型与名称板 */
    public static final int ENTITY_PLAYER = 1; // 实体类型：1=玩家，客户端据此渲染玩家模型与名称板

    /** 实体类型：2=怪物，可被选为 BattleStartCsReq.enemyEntityId */
    public static final int ENTITY_MONSTER = 2; // 实体类型：2=怪物，可被选为 BattleStartCsReq.enemyEntityId

    /** 实体类型：3=NPC，当前刷怪流程未生成，预留扩展 */
    public static final int ENTITY_NPC = 3; // 实体类型：3=NPC，当前刷怪流程未生成，预留扩展
    /** 实体类型：4=伙伴幽灵（不挡战斗碰撞，参与 AOI） */
    public static final int ENTITY_COMPANION_GHOST = 4;

    /** SyncEntityScNotify.syncType：新实体进入视野（含自己进场景时广播给他人） */
    public static final int SYNC_ENTER = 1; // SyncEntityScNotify.syncType：新实体进入视野（含自己进场景时广播给他人）

    /** SyncEntityScNotify.syncType：实体离开视野（切线、登出、怪物被击杀） */
    public static final int SYNC_LEAVE = 2; // SyncEntityScNotify.syncType：实体离开视野（切线、登出、怪物被击杀）

    /** SyncEntityScNotify.syncType：实体坐标变更（移动确认后广播） */
    public static final int SYNC_MOVE = 3; // SyncEntityScNotify.syncType：实体坐标变更（移动确认后广播）

    /** SyncEntityScNotify.syncType：属性变更（HP 等，当前未使用） */
    public static final int SYNC_ATTR = 4; // SyncEntityScNotify.syncType：属性变更（HP 等，当前未使用）

    /** 移动速度上限（像素/秒或与地图坐标系一致），超速请求拒绝防作弊 */
    private static final float MAX_MOVE_SPEED = 120f; // 移动速度上限（像素/秒或与地图坐标系一致），超速请求拒绝防作弊

    /** 默认 AOI 半径 / 格子边长（map_config 未配置时） */
    private static final float DEFAULT_AOI_RADIUS = 300f;
    private static final int DEFAULT_GRID_SIZE = 100;

    /** 配置查询：map_config、monster_config 带 Redis/本地缓存 */
    private final ConfigQueryService configQueryService;

    /** 大世界灰度配置解析（可选） */
    @Autowired(required = false)
    private SceneConfigResolver sceneConfigResolver;

    private final PlayerCachePort playerCachePort;

    /** 进场景策略，如副本等级门槛、活动地图开关 */
    private final ScenePolicy scenePolicy;

    /** 同分线玩家下行推送，SYNC_ENTITY_SC_NOTIFY 经此下发 */
    private final PlayerNotificationPort playerNotificationPort;

    /** 场景事件发布（RocketMQ 或 NoOp），上报 enter/move/leave 供日志与周边系统 */
    private final SceneEventPublisher sceneEventPublisher;

    /** 怪物波次工厂，首次进分线时按模板刷怪 */
    private final MonsterWaveSimpleFactory monsterWaveSimpleFactory;

    /** 跨场景传送经 Center 场景目录校验 */
    private final CenterSceneRouter centerSceneRouter;

    /** 跨节点迁移票据签发 */
    private final MigrationTicketService migrationTicketService;

    /** 断线重连快照 */
    private final SceneReconnectStore sceneReconnectStore;

    /** 分线容量 / 空线回收 / 重连宽限 */
    private final SceneRuntimeProperties sceneRuntimeProperties;

    /** 反作弊：移动超速/瞬移校验 */
    private final AntiCheatService antiCheatService;

    /** 动态域网格：密度拆合与边界交接检测 */
    private final WorldZoneManager worldZoneManager;
    private ZoneLoadBalancer zoneLoadBalancer;

    /** 大世界怪物归属锁（Redis SETNX / 本地回退） */
    private DistributedEntityLock entityLock = new DistributedEntityLock();

    /** 世界等级缩放（可选） */
    private WorldLevelManager worldLevelManager = new WorldLevelManager();

    /** 重连保护期（可选，默认 30s） */
    private ReconnectProtectionService reconnectProtection = new ReconnectProtectionService();

    /** 客户端预测校验（可选） */
    private MovementPredictionValidator movementValidator = new MovementPredictionValidator();

    /** NavMesh 路径可达性（可选） */
    private NavMeshPathValidator navMeshPathValidator = new NavMeshPathValidator();

    /** AOI 广播策略（可选） */
    private AoiBroadcastStrategy aoiBroadcastStrategy = new AoiBroadcastStrategy();

    /** 移动更新合并（可选） */
    private AoiUpdateBatcher aoiUpdateBatcher = new AoiUpdateBatcher();

    /** AOI 字段级增量编码（P12） */
    private AoiDeltaEncoder aoiDeltaEncoder = new AoiDeltaEncoder();

    /** 基本类型网格坐标存储（P12） */
    private PrimitiveGridStore primitiveGridStore = new PrimitiveGridStore();

    /** 移动指令对象池（P12） */
    private MoveCmdPool moveCmdPool = new MoveCmdPool();

    /** P15 ECS Tick / 动态同步频率 / 位级移动增量 / L1 位置缓存 */
    private SceneTickEngine sceneTickEngine = new SceneTickEngine();
    private SceneTickMicroPipeline tickMicroPipeline = new SceneTickMicroPipeline();
    private DynamicFrequencyService dynamicFrequency = new DynamicFrequencyService();
    private BroadcastImportanceFuseService broadcastFuse = new BroadcastImportanceFuseService();
    private MoveDeltaEncoder moveDeltaEncoder = new MoveDeltaEncoder();
    private MergedMoveAckService mergedMoveAck = new MergedMoveAckService();
    private LocalPositionCache localPositionCache = new LocalPositionCache();
    private RedisPositionBatchWriter redisPositionBatchWriter;

    /** 分线软硬顶调度（可选） */
    private LineShardScheduler lineShardScheduler = new LineShardScheduler();

    /** 实体 Debug TraceId（可选） */
    private EntityDebugTrace entityDebugTrace = new EntityDebugTrace();

    /** Zone 热迁移（可选） */
    private SceneHotMigrateService sceneHotMigrateService = new SceneHotMigrateService();

    /** 高密度同屏视觉降级调度（P12+） */
    private VisualSignificanceScheduler visualSignificance = new VisualSignificanceScheduler();

    private final AtomicLong moveTickSeq = new AtomicLong();

    /** 悬崖高低差视距裁剪（默认 ΔY≤8 才互相可见） */
    private final java.util.function.Predicate<float[]> cliffFilter = OcclusionCuller.cliffFilter(8f);

    /** 每玩家 500ms 位置历史，用于延迟补偿校验与客户端插值回放 */
    private final ConcurrentHashMap<Long, SnapshotBuffer> moveSnapshots = new ConcurrentHashMap<>();

    /** 分线运行时状态：键 "sceneId:lineId" -> 该线实体表与地图尺寸 */
    private final ConcurrentHashMap<String, SceneLineState> lineStates = new ConcurrentHashMap<>();

    /** 玩家当前所在场景引用：playerId -> (sceneId, lineId) */
    private final ConcurrentHashMap<Long, PlayerSceneRef> playerScene = new ConcurrentHashMap<>();

    /** 怪物 entityId 序列，从 5_000_000 起避免与玩家 ID 冲突 */
    private final AtomicLong monsterEntitySeq = new AtomicLong(5_000_000L);

    /** NPC entityId 序列，从 8_000_000 起 */
    private final AtomicLong npcEntitySeq = new AtomicLong(8_000_000L);

    /**
     * 构造器注入配置、缓存、策略、推送、事件与刷怪工厂。
     */
    public SceneActorService(
            ConfigQueryService configQueryService,
            PlayerCachePort playerCachePort,
            ScenePolicy scenePolicy,
            PlayerNotificationPort playerNotificationPort,
            SceneEventPublisher sceneEventPublisher,
            MonsterWaveSimpleFactory monsterWaveSimpleFactory,
            CenterSceneRouter centerSceneRouter,
            MigrationTicketService migrationTicketService,
            SceneReconnectStore sceneReconnectStore,
            SceneRuntimeProperties sceneRuntimeProperties) {
        this(configQueryService, playerCachePort, scenePolicy, playerNotificationPort,
                sceneEventPublisher, monsterWaveSimpleFactory, centerSceneRouter,
                migrationTicketService, sceneReconnectStore, sceneRuntimeProperties,
                new AntiCheatService(), new WorldZoneManager());
    }

    @Autowired
    public SceneActorService(
            ConfigQueryService configQueryService,
            PlayerCachePort playerCachePort,
            ScenePolicy scenePolicy,
            PlayerNotificationPort playerNotificationPort,
            SceneEventPublisher sceneEventPublisher,
            MonsterWaveSimpleFactory monsterWaveSimpleFactory,
            CenterSceneRouter centerSceneRouter,
            MigrationTicketService migrationTicketService,
            SceneReconnectStore sceneReconnectStore,
            SceneRuntimeProperties sceneRuntimeProperties,
            AntiCheatService antiCheatService,
            WorldZoneManager worldZoneManager) {
        this.configQueryService = configQueryService;
        this.playerCachePort = playerCachePort;
        this.scenePolicy = scenePolicy;
        this.playerNotificationPort = playerNotificationPort;
        this.sceneEventPublisher = sceneEventPublisher;
        this.monsterWaveSimpleFactory = monsterWaveSimpleFactory;
        this.centerSceneRouter = centerSceneRouter;
        this.migrationTicketService = migrationTicketService;
        this.sceneReconnectStore = sceneReconnectStore;
        this.sceneRuntimeProperties = sceneRuntimeProperties == null
                ? new SceneRuntimeProperties() : sceneRuntimeProperties;
        this.antiCheatService = antiCheatService == null ? new AntiCheatService() : antiCheatService;
        this.worldZoneManager = worldZoneManager == null ? new WorldZoneManager() : worldZoneManager;
        this.reconnectProtection.configure(this.sceneRuntimeProperties.getReconnectProtectionMs());
        this.lineShardScheduler.configure(
                this.sceneRuntimeProperties.getSoftPlayersPerLine(),
                this.sceneRuntimeProperties.getMaxPlayersPerLine(),
                this.sceneRuntimeProperties.getSoftCapBuffId());
        this.aoiUpdateBatcher.configure(this.sceneRuntimeProperties.getAoiBatchWindowMs(), 25f, 80f);
        configureAoiDistanceTiers();
    }

    private void configureAoiDistanceTiers() {
        float near = sceneRuntimeProperties.getAoiNearDistance();
        float mid = sceneRuntimeProperties.getAoiMidDistance();
        float far = sceneRuntimeProperties.getAoiFarDistance();
        this.aoiBroadcastStrategy.configureDistanceTiers(near, mid, far);
        this.aoiUpdateBatcher.configure(
                sceneRuntimeProperties.getAoiBatchWindowMs(), mid, far);
    }

    @Autowired(required = false)
    public void setZoneLoadBalancer(ZoneLoadBalancer zoneLoadBalancer) {
        this.zoneLoadBalancer = zoneLoadBalancer;
    }

    /**
     * 加载大世界 grid 配置：优先匹配灰度标签，失败回退全局基线。
     */
    public Map<String, Object> resolveOpenWorldCell(
            long playerId, long accountId, int zoneId, boolean betaTester, String gridCell) {
        if (sceneConfigResolver == null) {
            return Map.of("ok", false, "error", "scene_config_resolver_unavailable");
        }
        return sceneConfigResolver.resolveCell(playerId, accountId, zoneId, betaTester, gridCell);
    }

    @Autowired(required = false)
    public void setEntityLock(DistributedEntityLock entityLock) {
        if (entityLock != null) {
            this.entityLock = entityLock;
        }
    }

    @Autowired(required = false)
    public void setWorldLevelManager(WorldLevelManager worldLevelManager) {
        if (worldLevelManager != null) {
            this.worldLevelManager = worldLevelManager;
        }
    }

    @Autowired(required = false)
    public void setReconnectProtection(ReconnectProtectionService reconnectProtection) {
        if (reconnectProtection != null) {
            this.reconnectProtection = reconnectProtection;
            this.reconnectProtection.configure(sceneRuntimeProperties.getReconnectProtectionMs());
        }
    }

    @Autowired(required = false)
    public void setMovementValidator(MovementPredictionValidator movementValidator) {
        if (movementValidator != null) {
            this.movementValidator = movementValidator;
        }
    }

    @Autowired(required = false)
    public void setNavMeshPathValidator(NavMeshPathValidator navMeshPathValidator) {
        if (navMeshPathValidator != null) {
            this.navMeshPathValidator = navMeshPathValidator;
        }
    }

    @Autowired(required = false)
    public void setAoiBroadcastStrategy(AoiBroadcastStrategy aoiBroadcastStrategy) {
        if (aoiBroadcastStrategy != null) {
            this.aoiBroadcastStrategy = aoiBroadcastStrategy;
            configureAoiDistanceTiers();
        }
    }

    @Autowired(required = false)
    public void setAoiUpdateBatcher(AoiUpdateBatcher aoiUpdateBatcher) {
        if (aoiUpdateBatcher != null) {
            this.aoiUpdateBatcher = aoiUpdateBatcher;
            configureAoiDistanceTiers();
        }
    }

    @Autowired(required = false)
    public void setAoiDeltaEncoder(AoiDeltaEncoder aoiDeltaEncoder) {
        if (aoiDeltaEncoder != null) {
            this.aoiDeltaEncoder = aoiDeltaEncoder;
        }
    }

    @Autowired(required = false)
    public void setPrimitiveGridStore(PrimitiveGridStore primitiveGridStore) {
        if (primitiveGridStore != null) {
            this.primitiveGridStore = primitiveGridStore;
        }
    }

    @Autowired(required = false)
    public void setMoveCmdPool(MoveCmdPool moveCmdPool) {
        if (moveCmdPool != null) {
            this.moveCmdPool = moveCmdPool;
        }
    }

    @Autowired(required = false)
    public void setSceneTickMicroPipeline(SceneTickMicroPipeline tickMicroPipeline) {
        if (tickMicroPipeline != null) {
            this.tickMicroPipeline = tickMicroPipeline;
        }
    }

    @Autowired(required = false)
    public void setBroadcastImportanceFuseService(BroadcastImportanceFuseService broadcastFuse) {
        if (broadcastFuse != null) {
            this.broadcastFuse = broadcastFuse;
        }
    }

    @Autowired(required = false)
    public void setSceneTickEngine(SceneTickEngine sceneTickEngine) {
        if (sceneTickEngine != null) {
            this.sceneTickEngine = sceneTickEngine;
        }
    }

    @Autowired(required = false)
    public void setDynamicFrequencyService(DynamicFrequencyService dynamicFrequency) {
        if (dynamicFrequency != null) {
            this.dynamicFrequency = dynamicFrequency;
        }
    }

    @Autowired(required = false)
    public void setMoveDeltaEncoder(MoveDeltaEncoder moveDeltaEncoder) {
        if (moveDeltaEncoder != null) {
            this.moveDeltaEncoder = moveDeltaEncoder;
        }
    }

    @Autowired(required = false)
    public void setMergedMoveAckService(MergedMoveAckService mergedMoveAck) {
        if (mergedMoveAck != null) {
            this.mergedMoveAck = mergedMoveAck;
        }
    }

    @Autowired(required = false)
    public void setLocalPositionCache(LocalPositionCache localPositionCache) {
        if (localPositionCache != null) {
            this.localPositionCache = localPositionCache;
        }
    }

    @Autowired(required = false)
    public void setRedisPositionBatchWriter(RedisPositionBatchWriter redisPositionBatchWriter) {
        this.redisPositionBatchWriter = redisPositionBatchWriter;
    }

    @Autowired(required = false)
    public void setLineShardScheduler(LineShardScheduler lineShardScheduler) {
        if (lineShardScheduler != null) {
            this.lineShardScheduler = lineShardScheduler;
            this.lineShardScheduler.configure(
                    sceneRuntimeProperties.getSoftPlayersPerLine(),
                    sceneRuntimeProperties.getMaxPlayersPerLine(),
                    sceneRuntimeProperties.getSoftCapBuffId());
        }
    }

    @Autowired(required = false)
    public void setEntityDebugTrace(EntityDebugTrace entityDebugTrace) {
        if (entityDebugTrace != null) {
            this.entityDebugTrace = entityDebugTrace;
        }
    }

    @Autowired(required = false)
    public void setSceneHotMigrateService(SceneHotMigrateService sceneHotMigrateService) {
        if (sceneHotMigrateService != null) {
            this.sceneHotMigrateService = sceneHotMigrateService;
        }
    }

    /**
     * 处理进场景：校验地图/分线/策略 -> 移除旧场景实体 -> 写入新分线 -> 广播 SYNC_ENTER -> 返回可见列表。
     */
    public ProtocolMessage handleEnterScene(long playerId, EnterSceneCsReq req) { // 处理进场景：校验地图/分线/策略 -> 移除旧场景实体 -> 写入新分线 -> 广播 SYNC_ENTER -> 返回可见列表
        if (playerId <= 0) { // WebSocket 会话尚未绑定有效 playerId
            return enterRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0, 0, List.of()); // ENTER_SCENE_SC_RSP 携带 PLAYER_NOT_SELECTED
        }

        EnterSceneCsReq effectiveReq = req;
        MigrationTicketService.TicketPayload ticket = null;
        if (req.getSessionTicket() != null && !req.getSessionTicket().isBlank()) {
            ticket = migrationTicketService.consume(req.getSessionTicket());
            if (ticket == null || ticket.playerId() != playerId) {
                return enterRsp(RetCode.SESSION_TICKET_INVALID, 0, 0, 0, 0, 0, List.of());
            }
            EnterSceneCsReq.Builder b = EnterSceneCsReq.newBuilder()
                    .setSceneId(ticket.sceneId())
                    .setLineId(ticket.lineId())
                    .setEntryId(ticket.entryId())
                    .setPosX(ticket.posX())
                    .setPosY(ticket.posY())
                    .setPosZ(ticket.posZ());
            effectiveReq = b.build();
        }

        MapConfig map = configQueryService.findMapById(effectiveReq.getSceneId()); // 按 sceneId 查 map_config 宽高与 defaultLines
        if (map == null) { // sceneId 在 map_config 无记录
            return enterRsp(RetCode.SCENE_NOT_FOUND, 0, 0, 0, 0, 0, List.of()); // 目标地图不存在，拒绝进场景
        }
        if (!scenePolicy.allowEnterScene(map.getId(), playerId)) { // ScenePolicy 拒绝（等级不足/副本未开等）
            return enterRsp(RetCode.INTERNAL_ERROR, 0, 0, 0, 0, 0, List.of()); // 策略拒绝，客户端显示通用错误
        }
        int lineId = resolveAvailableLine(map, effectiveReq.getLineId());
        if (lineId < 1) {
            return enterRsp(RetCode.SCENE_LINE_FULL, map.getId(), 0, 0, 0, 0, List.of());
        }

        Player player = playerCachePort.findById(playerId); // 读 player 表缓存取角色名与等级
        if (player == null) { // playerId 不存在或已删号
            return enterRsp(RetCode.PLAYER_NOT_FOUND, 0, 0, 0, 0, 0, List.of()); // 角色不存在，拒绝进场景
        }

        sceneReconnectStore.clear(playerId);
        // 若玩家已在其他场景/分线，先移除旧实体并广播 SYNC_LEAVE
        removePlayerEntityEverywhere(playerId); // 清旧分线实体，避免同一 playerId 占两条线

        float[] pos = ticket != null && (ticket.posX() != 0f || ticket.posY() != 0f || ticket.posZ() != 0f)
                ? new float[]{
                    clamp(ticket.posX(), 0, map.getWidth()),
                    ticket.posY(),
                    clamp(ticket.posZ(), 0, map.getHeight())}
                : computeSpawnPosition(map, effectiveReq); // 按 entryId/客户端坐标/地图中心算出生点
        SceneLineState line = lineStates.computeIfAbsent(lineKey(map.getId(), lineId),
                k -> newLineState(map));
        line.emptySinceMillis = 0L;
        flushRespawns(line);
        ensureMonsters(line, map.getId()); // 分线首次有人进入时按刷怪点配置刷怪

        var pe = new SceneEntity(playerId, ENTITY_PLAYER, pos[0], pos[1], pos[2],
                player.getName(), player.getLevel() == null ? 1 : player.getLevel(), 0, playerId, null);
        putEntity(line, pe);
        playerScene.put(playerId, new PlayerSceneRef(map.getId(), lineId));
        lineShardScheduler.join(map.getId(), lineId, playerId);
        entityDebugTrace.assign(playerId);
        entityDebugTrace.record(playerId, EntityDebugTrace.Phase.ENTER,
                "scene=" + map.getId() + ":line=" + lineId, System.currentTimeMillis());

        var list = visibleEntities(line, pe); // 九宫格 AOI 可见列表
        sceneEventPublisher.publishEnterScene(playerId, map.getId(), lineId);

        var notifyOthers = SyncEntityScNotify.newBuilder()
                .setSyncType(SYNC_ENTER)
                .addEntityList(toProto(pe))
                .build();
        broadcastToAoiViewers(line, pe.x, pe.z, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, notifyOthers.toByteArray());

        return enterRsp(RetCode.OK, map.getId(), lineId, pe.x, pe.y, pe.z, list);
    }

    /**
     * 查询玩家当前场景信息：分线、自身坐标、同线其他可见实体（不含自己）。
     */
    public ProtocolMessage handleGetCurSceneInfo(long playerId, GetCurSceneInfoCsReq req) { // 查询玩家当前场景信息：分线、自身坐标、同线其他可见实体（不含自己）
        if (playerId <= 0) { // 会话未选角
            return curRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0, 0, List.of()); // GET_CUR_SCENE_INFO_SC_RSP 拒绝
        }
        var ref = playerScene.get(playerId); // 查 playerScene 得当前 sceneId+lineId
        if (ref == null) { // 玩家不在任何分线内存态
            return curRsp(RetCode.NOT_IN_SCENE, 0, 0, 0, 0, 0, List.of()); // 尚未进场景或已 leave
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 按 sceneId:lineId 取分线实体表
        if (line == null) { // 分线实例已被回收但 playerScene 残留
            playerScene.remove(playerId); // 清理过期 playerScene 索引
            return curRsp(RetCode.NOT_IN_SCENE, 0, 0, 0, 0, 0, List.of()); // 分线不存在，视为不在场景
        }
        var self = line.entities.get(playerId); // 取玩家自身 SceneEntity 读坐标
        if (self == null) { // 索引在但实体表无此 playerId
            return curRsp(RetCode.NOT_IN_SCENE, ref.sceneId, ref.lineId, 0, 0, 0, List.of()); // 实体缺失，NOT_IN_SCENE
        }
        flushRespawns(line);
        return curRsp(RetCode.OK, ref.sceneId, ref.lineId, self.x, self.y, self.z, visibleEntities(line, self));
    }

    /**
     * 处理移动：校验在场景内、速度、时间戳、NavMesh、预测偏差 -> 更新坐标 -> 批量 AOI 广播 -> 返回确认坐标。
     * 偏差过大时服务器权威回滚并累加 AntiCheat 违规积分。
     */
    public ProtocolMessage handleMove(long playerId, MoveCsReq req) { // 处理移动：校验在场景内、速度、时间戳 -> 更新坐标 -> 广播 SYNC_MOVE -> 返回确认坐标
        if (playerId <= 0) { // 未选角
            return moveRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0); // MOVE_SC_RSP 拒绝
        }
        var ref = playerScene.get(playerId); // 确认玩家已进场景
        if (ref == null) { // 不在 playerScene 映射
            return moveRsp(RetCode.NOT_IN_SCENE, 0, 0, 0); // 未进场景不能移动
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 取当前分线实体表
        if (line == null) { // 分线实例不存在
            return moveRsp(RetCode.NOT_IN_SCENE, 0, 0, 0); // 分线已失效
        }
        var self = line.entities.get(playerId); // 取玩家 SceneEntity 待更新坐标
        if (self == null) { // 实体表无此玩家
            return moveRsp(RetCode.NOT_IN_SCENE, 0, 0, 0); // 实体缺失
        }
        long now = System.currentTimeMillis(); // 服务端权威毫秒时间戳
        float ox = self.x;
        float oy = self.y;
        float oz = self.z;
        float nx = clamp(req.getTargetX(), 0, line.width);
        float ny = req.getTargetY();
        float nz = clamp(req.getTargetZ(), 0, line.height);

        // NavMesh 路径可达性：穿墙/越障直接拒绝
        NavMeshPathValidator.PathCheckResult path =
                navMeshPathValidator.validate(ref.sceneId, ox, oz, nx, nz);
        if (!path.reachable()) {
            antiCheatService.strike(playerId, "navmesh_" + path.reason());
            return moveRsp(RetCode.MOVE_REJECTED, ox, oy, oz);
        }

        AntiCheatService.CheckResult cheat = antiCheatService.checkMove(
                playerId, ox, oy, oz, nx, ny, nz, req.getSpeed(), req.getTimestamp(), now);
        if (cheat.verdict() != AntiCheatService.Verdict.OK) {
            // 高延迟：仅在已有位置历史时，回滚到客户端时间点再判定；无历史仍拒绝
            SnapshotBuffer buf = moveSnapshots.get(playerId);
            boolean lagOk = buf != null && buf.size() > 0
                    && buf.validateWithLagCompensation(
                    req.getTimestamp(), nx, ny, nz, Math.max(1f, req.getSpeed()), 8f);
            if (!lagOk) {
                return moveRsp(RetCode.MOVE_REJECTED, ox, oy, oz);
            }
        }

        // 服务器权威：目的地协议下用 lastAccepted 隐含速度检测变速齿轮；超速回滚并联动 AntiCheat
        MovementPredictionValidator.ActionType action =
                req.getSpeed() > 40f
                        ? MovementPredictionValidator.ActionType.DASH
                        : MovementPredictionValidator.ActionType.MOVE;
        // 权威坐标取目标点 → 位置偏差恒为 0，真正拦截靠 lastAccepted 速度倍率
        MovementPredictionValidator.ValidationResult predicted = movementValidator.validate(
                playerId, action, nx, ny, nz, nx, ny, nz, req.getTimestamp(), now);
        if (!predicted.accepted()) {
            if (predicted.rollbackRequired()) {
                antiCheatService.strike(playerId, "rollback_" + predicted.reason());
            }
            entityDebugTrace.record(playerId, EntityDebugTrace.Phase.MOVE,
                    "rollback:" + predicted.reason(), now);
            return moveRsp(RetCode.MOVE_REJECTED, ox, oy, oz);
        }
        if (predicted.corrected()) {
            nx = ox;
            ny = oy;
            nz = oz;
        }

        moveSnapshots.computeIfAbsent(playerId, id -> SnapshotBuffer.forLagCompensation())
                .push(now, ox, oy, oz, req.getSpeed());

        // 动态域：跨 Zone 且异节点时签发无缝交接票据
        WorldZoneManager.BorderHandoff handoff =
                worldZoneManager.detectHandoff(ref.sceneId, ox, oz, nx, nz, line.gridSize);
        if (handoff.required() && handoff.targetNodeId() != null && !handoff.targetNodeId().isBlank()
                && !"local".equals(handoff.targetNodeId())) {
            String ticket = migrationTicketService.issueSeamless(
                    playerId, ref.sceneId, ref.lineId, 0, nx, ny, nz, 0f, 0f, 0f, handoff.toZoneId());
            entityDebugTrace.record(playerId, EntityDebugTrace.Phase.MIGRATE,
                    "handoff:" + handoff.fromZoneId() + "->" + handoff.toZoneId() + ":" + ticket, now);
        }

        Set<Long> oldViewers = aoiPlayerIds(line, ox, oz, playerId);
        moveEntity(line, self, nx, ny, nz);
        syncComponentMove(line, playerId, ox, oy, oz, nx, ny, nz, req.getSpeed(), now);
        flushRespawns(line);
        Set<Long> newViewers = aoiPlayerIds(line, nx, nz, playerId);
        refreshZoneDensity(ref.sceneId, line);
        entityDebugTrace.record(playerId, EntityDebugTrace.Phase.MOVE,
                "pos=" + nx + "," + ny + "," + nz, now);

        sceneEventPublisher.publishMove(playerId, ref.sceneId, nx, ny, nz);

        // 离开旧视野的玩家收到 SYNC_LEAVE
        Set<Long> left = new HashSet<>(oldViewers);
        left.removeAll(newViewers);
        if (!left.isEmpty()) {
            var leave = SyncEntityScNotify.newBuilder()
                    .setSyncType(SYNC_LEAVE)
                    .addLeaveEntityIds(playerId)
                    .build()
                    .toByteArray();
            for (Long viewerId : left) {
                playerNotificationPort.send(viewerId, MessageId.SYNC_ENTITY_SC_NOTIFY, leave);
            }
        }
        // 新进入视野的玩家收到 SYNC_ENTER
        Set<Long> entered = new HashSet<>(newViewers);
        entered.removeAll(oldViewers);
        if (!entered.isEmpty()) {
            var enter = SyncEntityScNotify.newBuilder()
                    .setSyncType(SYNC_ENTER)
                    .addEntityList(toProto(self))
                    .build()
                    .toByteArray();
            for (Long viewerId : entered) {
                playerNotificationPort.send(viewerId, MessageId.SYNC_ENTITY_SC_NOTIFY, enter);
            }
        }
        // 仍在视野内：先入批，再按距离分级广播（防 AOI 风暴）
        Set<Long> stayed = new HashSet<>(newViewers);
        stayed.retainAll(oldViewers);
        aoiUpdateBatcher.enqueue(playerId, nx, ny, nz, 0, SYNC_MOVE, now);
        aoiDeltaEncoder.encode(playerId, nx, ny, nz, 0, SYNC_MOVE);
        primitiveGridStore.upsert(playerId, nx, ny, nz);
        SceneMoveCmd moveCmd = toSceneMoveCmd(req, nx, ny, nz, now);
        moveDeltaEncoder.encode(playerId, moveCmd);
        localPositionCache.put(playerId, nx, ny, nz, now);
        if (redisPositionBatchWriter != null) {
            redisPositionBatchWriter.enqueue(playerId, nx, ny, nz, now);
        }
        MoveCmdPool.Slot moveSlot = moveCmdPool.acquireWalk(nx, ny, nz, req.getSpeed(), now);
        moveCmdPool.release(moveSlot);
        long tick = moveTickSeq.incrementAndGet();
        if (!stayed.isEmpty() && aoiUpdateBatcher.shouldFlush(now)) {
            aoiUpdateBatcher.flushAll(now);
        }
        if (!stayed.isEmpty()) {
            List<Long> recipients = aoiBroadcastStrategy.selectRecipients(
                    AoiBroadcastStrategy.Mode.INTEREST_ONLY, stayed, stayed, Set.of(playerId));
            var move = SyncEntityScNotify.newBuilder()
                    .setSyncType(SYNC_MOVE)
                    .addEntityList(toProto(self))
                    .build()
                    .toByteArray();
            for (Long viewerId : recipients) {
                SceneEntity viewer = line.entities.get(viewerId);
                float dist = viewer == null ? 0f
                        : (float) Math.sqrt(
                        (viewer.x - nx) * (viewer.x - nx) + (viewer.z - nz) * (viewer.z - nz));
                float relSpeed = Math.max(0f, req.getSpeed());
                if (!dynamicFrequency.shouldSync(viewerId, dist, relSpeed, now)) {
                    continue;
                }
                AoiBroadcastStrategy.FrequencyTier tier = aoiBroadcastStrategy.frequencyTier(dist);
                BroadcastImportanceFuseService.SyncDecision fuse =
                        broadcastFuse.decide(playerId, dist, tier, now);
                if (!fuse.allow()) {
                    continue;
                }
                AoiBroadcastStrategy.FrequencyTier effectiveTier = fuse.effectiveTier();
                if (!aoiBroadcastStrategy.allowAtTick(effectiveTier, tick)) {
                    continue;
                }
                if (!aoiBroadcastStrategy.shouldBroadcast(
                        AoiBroadcastStrategy.Mode.DELTA_COMPRESSED, true, dist, 1f)) {
                    continue;
                }
                int syncType = effectiveTier == AoiBroadcastStrategy.FrequencyTier.OUT_STATE_ONLY
                        ? SYNC_ATTR : SYNC_MOVE;
                byte[] payload;
                if (syncType == SYNC_ATTR) {
                    payload = SyncEntityScNotify.newBuilder()
                            .setSyncType(SYNC_ATTR)
                            .addEntityList(toProto(self))
                            .build()
                            .toByteArray();
                } else if (fuse.positionOnly()) {
                    var posOnly = SyncEntityScNotify.newBuilder()
                            .setSyncType(SYNC_MOVE)
                            .addEntityList(toProto(self).toBuilder().clearModelId().build())
                            .build()
                            .toByteArray();
                    payload = posOnly;
                } else {
                    payload = move;
                }
                broadcastFuse.recordOutboundBytes(payload.length, now);
                playerNotificationPort.send(viewerId, MessageId.SYNC_ENTITY_SC_NOTIFY, payload);
            }
        }

        return moveRsp(RetCode.OK, nx, ny, nz);
    }

    /**
     * 切换分线：从旧线移除并 SYNC_LEAVE -> 在新线中心出生并 SYNC_ENTER -> 返回新线可见实体。
     */
    public ProtocolMessage handleSwitchLine(long playerId, SwitchLineCsReq req) { // 切换分线：从旧线移除并 SYNC_LEAVE -> 在新线中心出生并 SYNC_ENTER -> 返回新线可见实体
        if (playerId <= 0) { // 未选角
            return switchRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0, 0, List.of()); // SWITCH_LINE_SC_RSP 拒绝
        }
        var ref = playerScene.get(playerId); // 必须在场景中才能切线
        if (ref == null) { // 未进场景
            return switchRsp(RetCode.NOT_IN_SCENE, 0, 0, 0, 0, 0, List.of()); // NOT_IN_SCENE
        }
        MapConfig map = configQueryService.findMapById(ref.sceneId); // 同地图内切分线，查 defaultLines
        if (map == null) { // 当前 sceneId 配置已删
            return switchRsp(RetCode.SCENE_NOT_FOUND, 0, 0, 0, 0, 0, List.of()); // 地图不存在
        }
        int target = req.getTargetLineId(); // 客户端请求的目标分线号
        int maxLines = Math.max(map.getDefaultLines() == null ? 1 : map.getDefaultLines(),
                sceneRuntimeProperties.getMaxLinesPerMap());
        if (target < 1 || target > maxLines || target == ref.lineId) { // 非法分线或切到当前线
            return switchRsp(RetCode.INVALID_LINE, map.getId(), ref.lineId, 0, 0, 0, List.of()); // INVALID_LINE
        }
        if (countPlayersOnLine(map.getId(), target) >= sceneRuntimeProperties.getMaxPlayersPerLine()) {
            return switchRsp(RetCode.SCENE_LINE_FULL, map.getId(), ref.lineId, 0, 0, 0, List.of());
        }
        LineShardScheduler.LineAdmit admit = lineShardScheduler.admit(
                map.getId(), target, maxLines);
        if (admit.result() == LineShardScheduler.AdmitResult.HARD_CAP_REJECT
                || admit.result() == LineShardScheduler.AdmitResult.INVALID_LINE) {
            return switchRsp(RetCode.SCENE_LINE_FULL, map.getId(), ref.lineId, 0, 0, 0, List.of());
        }

        Player player = playerCachePort.findById(playerId); // 切线后重建 EntityInfo 需 name/level
        if (player == null) { // 角色不存在
            return switchRsp(RetCode.PLAYER_NOT_FOUND, 0, 0, 0, 0, 0, List.of()); // PLAYER_NOT_FOUND
        }

        SceneLineState oldLine = lineStates.get(lineKey(ref.sceneId, ref.lineId));
        if (oldLine != null) {
            SceneEntity oldSelf = oldLine.entities.get(playerId);
            float bx = oldSelf != null ? oldSelf.x : map.getWidth() / 2f;
            float bz = oldSelf != null ? oldSelf.z : map.getHeight() / 2f;
            removeEntity(oldLine, playerId);
            var leave = SyncEntityScNotify.newBuilder()
                    .setSyncType(SYNC_LEAVE)
                    .addLeaveEntityIds(playerId)
                    .build();
            broadcastToAoiViewers(oldLine, bx, bz, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, leave.toByteArray());
        }

        float cx = map.getWidth() / 2f;
        float cz = map.getHeight() / 2f;
        SceneLineState newLine = lineStates.computeIfAbsent(lineKey(map.getId(), target),
                k -> newLineState(map));
        newLine.emptySinceMillis = 0L;
        flushRespawns(newLine);
        ensureMonsters(newLine, map.getId());

        var pe = new SceneEntity(playerId, ENTITY_PLAYER, cx, 0f, cz,
                player.getName(), player.getLevel() == null ? 1 : player.getLevel(), 0, playerId, null);
        putEntity(newLine, pe);
        playerScene.put(playerId, new PlayerSceneRef(map.getId(), target));
        lineShardScheduler.leave(map.getId(), ref.lineId, playerId);
        lineShardScheduler.join(map.getId(), target, playerId);
        entityDebugTrace.record(playerId, EntityDebugTrace.Phase.ENTER,
                "switch_line=" + ref.lineId + "->" + target, System.currentTimeMillis());

        sceneEventPublisher.publishSwitchLine(playerId, map.getId(), ref.lineId, target);

        var enterNotify = SyncEntityScNotify.newBuilder()
                .setSyncType(SYNC_ENTER)
                .addEntityList(toProto(pe))
                .build();
        broadcastToAoiViewers(newLine, pe.x, pe.z, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, enterNotify.toByteArray());

        return switchRsp(RetCode.OK, map.getId(), target, pe.x, pe.y, pe.z, visibleEntities(newLine, pe));
    }

    /**
     * AOI 范围查询：以请求圆心+半径做三维距离平方比较，返回范围内 EntityInfo 列表。
     */
    public ProtocolMessage handleGetNearby(long playerId, GetNearbyEntitiesCsReq req) { // AOI 范围查询：以请求圆心+半径做三维距离平方比较，返回范围内 EntityInfo 列表
        if (playerId <= 0) { // 未选角
            return nearbyRsp(RetCode.PLAYER_NOT_SELECTED, List.of()); // GET_NEARBY_ENTITIES_SC_RSP 空列表
        }
        var ref = playerScene.get(playerId); // 必须在场景内才能做 AOI
        if (ref == null) { // 未进场景
            return nearbyRsp(RetCode.NOT_IN_SCENE, List.of()); // NOT_IN_SCENE
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 当前分线实体全集
        if (line == null) { // 分线不存在
            return nearbyRsp(RetCode.NOT_IN_SCENE, List.of()); // 分线已失效
        }
        float r = req.getRadius() > 0 ? req.getRadius() : line.aoiRadius;
        if (r <= 0) {
            return nearbyRsp(RetCode.INTERNAL_ERROR, List.of());
        }
        flushRespawns(line);
        float cx = req.getCenterX();
        float cy = req.getCenterY();
        float cz = req.getCenterZ();
        float r2 = r * r;
        List<EntityInfo> out = new ArrayList<>();
        for (SceneEntity e : entitiesInAoiCells(line, cx, cy, cz, r)) {
            if (e.inCombat) {
                continue;
            }
            float dx = e.x - cx;
            float dy = e.y - cy;
            float dz = e.z - cz;
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            if (!cliffFilter.test(new float[]{cx, cy, cz, e.x, e.y, e.z})) {
                continue;
            }
            out.add(toProto(e));
        }
        return nearbyRsp(RetCode.OK, out);
    }

    /**
     * 跨场景传送：离开当前场景后进入目标场景（同进程多地图分线；跨服时由 Center 路由至目标场景节点）。
     */
    public ProtocolMessage handleTransferScene(long playerId, TransferSceneCsReq req) {
        if (playerId <= 0) {
            return transferRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0, 0, List.of(), "", 0, "");
        }
        if (req.getTargetSceneId() <= 0) {
            return transferRsp(RetCode.SCENE_TRANSFER_REJECTED, 0, 0, 0, 0, 0, List.of(), "", 0, "");
        }
        SceneMigrationPlan plan;
        try {
            plan = centerSceneRouter.planMigration(req.getTargetSceneId());
        } catch (Exception e) {
            return transferRsp(RetCode.SCENE_TRANSFER_REJECTED, 0, 0, 0, 0, 0, List.of(), "", 0, "");
        }
        if (!plan.local()) {
            if (plan.nodeHost() == null || plan.nodeHost().isBlank() || plan.nodePort() <= 0) {
                return transferRsp(RetCode.SCENE_TRANSFER_NO_NODE, plan.sceneId(), req.getTargetLineId(),
                        0, 0, 0, List.of(), "", 0, "");
            }
            float[] carry = currentPlayerPos(playerId);
            int targetLine = req.getTargetLineId() <= 0 ? 0 : req.getTargetLineId();
            // 跨节点无缝交接：票据携带速度/朝向/zone，降低硬切感
            removePlayerEntityEverywhere(playerId);
            String ticket = migrationTicketService.issueSeamless(
                    playerId, req.getTargetSceneId(), targetLine, req.getEntryId(),
                    carry[0], carry[1], carry[2], 0f, 0f, 0f, plan.zoneId());
            return transferRsp(RetCode.SCENE_TRANSFER_REDIRECT, plan.sceneId(), targetLine,
                    carry[0], carry[1], carry[2], List.of(), plan.nodeHost(), plan.nodePort(), ticket);
        }
        EnterSceneCsReq enterReq = EnterSceneCsReq.newBuilder()
                .setSceneId(req.getTargetSceneId())
                .setLineId(req.getTargetLineId())
                .setEntryId(req.getEntryId())
                .build();
        ProtocolMessage enterMsg = handleEnterScene(playerId, enterReq);
        try {
            EnterSceneScRsp enterRsp = EnterSceneScRsp.parseFrom(enterMsg.payload());
            return transferRsp(
                    enterRsp.getRetcode(),
                    enterRsp.getSceneId(),
                    enterRsp.getLineId(),
                    enterRsp.getPosX(),
                    enterRsp.getPosY(),
                    enterRsp.getPosZ(),
                    enterRsp.getEntityListList(),
                    "", 0, "");
        } catch (Exception e) {
            return transferRsp(RetCode.INTERNAL_ERROR, 0, 0, 0, 0, 0, List.of(), "", 0, "");
        }
    }

    private ProtocolMessage transferRsp(
            int retcode, int sceneId, int lineId, float x, float y, float z, List<EntityInfo> entities,
            String redirectHost, int redirectPort, String sessionTicket) {
        var b = TransferSceneScRsp.newBuilder()
                .setRetcode(retcode)
                .setSceneId(sceneId)
                .setLineId(lineId)
                .setPosX(x)
                .setPosY(y)
                .setPosZ(z)
                .setRedirectHost(redirectHost == null ? "" : redirectHost)
                .setRedirectPort(redirectPort)
                .setSessionTicket(sessionTicket == null ? "" : sessionTicket);
        entities.forEach(b::addEntityList);
        return new ProtocolMessage(MessageId.TRANSFER_SCENE_SC_RSP, b.build().toByteArray());
    }

    /**
     * 主动登出：清除重连快照、解除推送绑定并从场景移除实体。
     */
    public void onPlayerLeave(long playerId) { // 断线或登出：解除 PushRegistry 绑定并从场景移除玩家实体、广播 SYNC_LEAVE
        if (playerId <= 0) { // 无效 playerId 无需清理
            return; // 直接结束，避免误删
        }
        sceneReconnectStore.clear(playerId);
        playerNotificationPort.unbind(playerId); // 解除 WebSocket→playerId 推送绑定
        removePlayerEntityEverywhere(playerId); // 从分线移除实体并广播 SYNC_LEAVE
    }

    /**
     * TCP/WS 断线：保存重连快照后离开场景（宽限期内可 ResumeScene）。
     *
     * @return resumeToken，无可快照时为空串
     */
    public String onPlayerDisconnect(long playerId) {
        if (playerId <= 0) {
            return "";
        }
        String token = "";
        var ref = playerScene.get(playerId);
        if (ref != null) {
            SceneLineState line = lineStates.get(lineKey(ref.sceneId(), ref.lineId()));
            SceneEntity self = line == null ? null : line.entities.get(playerId);
            if (self != null) {
                token = sceneReconnectStore.save(playerId, ref.sceneId(), ref.lineId(),
                        self.x, self.y, self.z);
            }
        }
        removePlayerEntityEverywhere(playerId);
        return token;
    }

    /**
     * 断线宽限内恢复场景：消费 resumeToken 后按快照重新进场。
     */
    public ProtocolMessage handleResumeScene(long playerId, ResumeSceneCsReq req) {
        if (playerId <= 0) {
            return resumeRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0, 0, List.of(), "");
        }
        String resumeToken = req == null ? "" : req.getResumeToken();
        SceneReconnectStore.Snapshot snap = sceneReconnectStore.consume(playerId, resumeToken);
        if (snap == null) {
            return resumeRsp(RetCode.RESUME_EXPIRED, 0, 0, 0, 0, 0, List.of(), "");
        }
        EnterSceneCsReq enterReq = EnterSceneCsReq.newBuilder()
                .setSceneId(snap.sceneId())
                .setLineId(snap.lineId())
                .setPosX(snap.posX())
                .setPosY(snap.posY())
                .setPosZ(snap.posZ())
                .build();
        ProtocolMessage enterMsg = handleEnterScene(playerId, enterReq);
        reconnectProtection.configure(sceneRuntimeProperties.getReconnectProtectionMs());
        reconnectProtection.grant(playerId, System.currentTimeMillis());
        try {
            EnterSceneScRsp enterRsp = EnterSceneScRsp.parseFrom(enterMsg.payload());
            return resumeRsp(enterRsp.getRetcode(), enterRsp.getSceneId(), enterRsp.getLineId(),
                    enterRsp.getPosX(), enterRsp.getPosY(), enterRsp.getPosZ(),
                    enterRsp.getEntityListList(), "");
        } catch (Exception e) {
            return resumeRsp(RetCode.INTERNAL_ERROR, 0, 0, 0, 0, 0, List.of(), "");
        }
    }

    /** 重连保护期内不可被选为战斗目标 / 受击。 */
    public boolean isReconnectProtected(long playerId) {
        return reconnectProtection.isProtected(playerId, System.currentTimeMillis());
    }

    public ReconnectProtectionService reconnectProtection() {
        return reconnectProtection;
    }

    /** 空线超时回收（无玩家实体的分线实例）。 */
    @Scheduled(fixedDelayString = "${game.scene.empty-line-gc-ms:30000}")
    public void gcEmptyLines() {
        long ttl = Math.max(5_000L, sceneRuntimeProperties.getEmptyLineTtlMs());
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, SceneLineState>> it = lineStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SceneLineState> e = it.next();
            SceneLineState line = e.getValue();
            if (countPlayers(line) > 0) {
                line.emptySinceMillis = 0L;
                continue;
            }
            if (line.emptySinceMillis <= 0L) {
                line.emptySinceMillis = now;
                continue;
            }
            if (now - line.emptySinceMillis >= ttl) {
                it.remove();
            }
        }
    }

    /** 当前内存中活跃的分线实例数量，供 JMX/监控 */
    public int getSceneLineCount() { // 当前内存中活跃的分线实例数量，供 JMX/监控
        return lineStates.size(); // lineStates 键数量=已创建分线数
    }

    /** 全部分线实体总数（玩家+怪物），供监控 */
    public int getTotalEntityCount() { // 全部分线实体总数（玩家+怪物），供监控
        int n = 0; // 累计实体计数
        for (var line : lineStates.values()) { // 遍历每条分线
            n += line.entities.size(); // 累加该线 entities 表大小
        }
        return n; // 全服内存实体总数
    }

    /** 动态域网格统计（世界=sceneId）。 */
    public java.util.Map<String, Object> worldZoneStats(int worldId) {
        worldZoneManager.ensureWorld(worldId, "local");
        return worldZoneManager.stats(worldId);
    }

    public WorldZoneManager worldZoneManager() {
        return worldZoneManager;
    }

    public AntiCheatService antiCheatService() {
        return antiCheatService;
    }

    /**
     * AOI 网格 2D 热力图：返回各 cell 实体密度，供 Grafana / 运维可视化。
     */
    public Map<String, Object> debugAoiGrid(int sceneId, int lineId) {
        SceneLineState line = lineStates.get(lineKey(sceneId, lineId));
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("ok", line != null);
        body.put("sceneId", sceneId);
        body.put("lineId", lineId);
        if (line == null) {
            body.put("error", "line_not_found");
            return body;
        }
        body.put("gridSize", line.gridSize);
        body.put("width", line.width);
        body.put("height", line.height);
        body.put("entityCount", line.entities.size());
        body.put("playerCount", countPlayers(line));
        java.util.List<Map<String, Object>> cells = new java.util.ArrayList<>();
        int maxHeat = 0;
        for (var e : line.aoiGrid.snapshotCells().entrySet()) {
            long key = e.getKey();
            int gx = (int) (key >> 32);
            int gz = (int) key;
            int heat = e.getValue() == null ? 0 : e.getValue().size();
            maxHeat = Math.max(maxHeat, heat);
            Map<String, Object> cell = new java.util.LinkedHashMap<>();
            cell.put("gx", gx);
            cell.put("gz", gz);
            cell.put("heat", heat);
            cells.add(cell);
        }
        body.put("maxHeat", maxHeat);
        body.put("cells", cells);
        body.put("aoiBroadcast", aoiBroadcastStrategy.stats());
        body.put("aoiBatcher", aoiUpdateBatcher.stats());
        body.put("aoiDelta", aoiDeltaEncoder.stats());
        body.put("primitiveGrid", primitiveGridStore.stats());
        body.put("moveCmdPool", moveCmdPool.stats());
        return body;
    }

    public Map<String, Object> debugEntityTrace(long entityId) {
        return entityDebugTrace.view(entityId);
    }

    public LineShardScheduler lineShardScheduler() {
        return lineShardScheduler;
    }

    public SceneHotMigrateService sceneHotMigrateService() {
        return sceneHotMigrateService;
    }

    public NavMeshPathValidator navMeshPathValidator() {
        return navMeshPathValidator;
    }

    /**
     * 同线组队强制拉入：队长所在线 + 坐标，为异线队友签发换线票据。
     */
    public Map<String, Object> pullPartyToLeaderLine(long leaderId, java.util.List<Long> memberIds) {
        var ref = playerScene.get(leaderId);
        if (ref == null) {
            return Map.of("ok", false, "error", "leader_not_in_scene");
        }
        float[] pos = currentPlayerPos(leaderId);
        Map<Long, Integer> currentLines = new java.util.LinkedHashMap<>();
        if (memberIds != null) {
            for (Long mid : memberIds) {
                var mref = playerScene.get(mid);
                if (mref != null) {
                    currentLines.put(mid, mref.lineId);
                }
            }
        }
        var tickets = lineShardScheduler.pullPartyToLeaderLine(
                leaderId, ref.sceneId, ref.lineId, pos[0], pos[1], pos[2], memberIds, currentLines);
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (var t : tickets) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("memberId", t.memberId());
            row.put("sceneId", t.sceneId());
            row.put("targetLineId", t.targetLineId());
            row.put("x", t.x());
            row.put("y", t.y());
            row.put("z", t.z());
            row.put("sessionTicket", t.sessionTicket());
            rows.add(row);
        }
        return Map.of("ok", true, "leaderId", leaderId, "lineId", ref.lineId, "tickets", rows);
    }

    private void refreshZoneDensity(int sceneId, SceneLineState line) {
        WorldZoneManager.ZoneShard zone = worldZoneManager.ensureWorld(sceneId, "local");
        int players = countPlayers(line);
        worldZoneManager.reportPlayerCount(sceneId, zone.zoneId(), players);
        if (zoneLoadBalancer != null) {
            zoneLoadBalancer.rebalance(sceneId, Map.of(zone.zoneId(), players));
        }
    }

    @Override
    public Optional<MonsterBattleRef> findMonsterForBattle(long playerId, long enemyEntityId) {
        return findLocalMonsterForBattle(playerId, enemyEntityId)
                .filter(r -> !r.inCombat())
                .map(r -> new MonsterBattleRef(r.enemyEntityId(), r.sceneId(), r.monsterTemplateId()));
    }

    @Override
    public Optional<EncounterLock> markMonsterInCombat(long playerId, long enemyEntityId) {
        var local = findLocalMonsterForBattle(playerId, enemyEntityId);
        if (local.isEmpty()) {
            return Optional.empty();
        }
        var ref = playerScene.get(playerId);
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId));
        if (line == null) {
            return Optional.empty();
        }
        SceneEntity monster = line.entities.get(enemyEntityId);
        SceneEntity player = line.entities.get(playerId);
        if (monster == null || monster.entityType != ENTITY_MONSTER || monster.inCombat) {
            return Optional.empty();
        }
        // 分布式归属锁：多人对同一野怪开战时仅归属队伍可持锁推进结算
        DistributedEntityLock.LockHandle handle = entityLock.tryAcquire(
                ref.sceneId, enemyEntityId, playerId, Duration.ofSeconds(120));
        if (!handle.acquired()) {
            return Optional.empty();
        }
        monster.inCombat = true;
        float rx = player != null ? player.x : monster.x;
        float ry = player != null ? player.y : 0f;
        float rz = player != null ? player.z : monster.z;
        // 遭遇中对 AOI 他人隐藏怪物
        var hide = SyncEntityScNotify.newBuilder()
                .setSyncType(SYNC_LEAVE)
                .addLeaveEntityIds(enemyEntityId)
                .build();
        broadcastToAoiViewers(line, monster.x, monster.z, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, hide.toByteArray());
        return Optional.of(new EncounterLock(
                monster.entityId, ref.sceneId, monster.monsterTemplateId, rx, ry, rz));
    }

    @Override
    public void releaseMonsterFromCombat(long playerId, long enemyEntityId) {
        var ref = playerScene.get(playerId);
        if (ref == null) {
            return;
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId));
        if (line == null) {
            return;
        }
        SceneEntity monster = line.entities.get(enemyEntityId);
        if (monster == null || monster.entityType != ENTITY_MONSTER) {
            return;
        }
        entityLock.releaseByOwner(ref.sceneId, enemyEntityId, playerId);
        monster.inCombat = false;
        var show = SyncEntityScNotify.newBuilder()
                .setSyncType(SYNC_ENTER)
                .addEntityList(toProto(monster))
                .build();
        broadcastToAoiViewers(line, monster.x, monster.z, -1L, MessageId.SYNC_ENTITY_SC_NOTIFY, show.toByteArray());
    }

    /**
     * 校验玩家当前分线内是否存在该怪物实体，供 BattleStart 前置校验。
     */
    public Optional<LocalMonsterBattleRef> findLocalMonsterForBattle(long playerId, long enemyEntityId) {
        if (playerId <= 0) {
            return Optional.empty();
        }
        var ref = playerScene.get(playerId);
        if (ref == null) {
            return Optional.empty();
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId));
        if (line == null) {
            return Optional.empty();
        }
        var e = line.entities.get(enemyEntityId);
        if (e == null || e.entityType != ENTITY_MONSTER || e.monsterTemplateId == null) {
            return Optional.empty();
        }
        return Optional.of(new LocalMonsterBattleRef(
                e.entityId, ref.sceneId, e.monsterTemplateId, e.name, e.level, e.inCombat));
    }

    /**
     * 同分线内两实体三维欧氏距离，供 SkillService 施法范围校验。
     */
    public Optional<Float> distanceBetweenEntities(long playerId, long targetEntityId) { // 同分线内两实体三维欧氏距离，供 SkillService 施法范围校验
        var ref = playerScene.get(playerId); // 施法者场景引用
        if (ref == null) { // 施法者不在场景
            return Optional.empty(); // 无法算距离
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 同分线实体表
        if (line == null) { // 分线不存在
            return Optional.empty(); // 无实体表
        }
        var self = line.entities.get(playerId); // 施法者坐标
        var target = line.entities.get(targetEntityId); // 目标实体坐标
        if (self == null || target == null) { // 任一方不在同线
            return Optional.empty(); // 距离无意义
        }
        float dx = self.x - target.x; // X 差
        float dy = self.y - target.y; // Y 差
        float dz = self.z - target.z; // Z 差
        return Optional.of((float) Math.sqrt(dx * dx + dy * dy + dz * dz)); // 三维欧氏距离，与 skill_config.range 比较
    }

    /**
     * 玩家当前位置到地面目标点的距离，供范围技能（无 entityId、仅坐标）校验。
     */
    public Optional<Float> distancePlayerToPoint(long playerId, float tx, float ty, float tz) { // 玩家当前位置到地面目标点的距离，供范围技能（无 entityId、仅坐标）校验
        var ref = playerScene.get(playerId); // 施法者场景引用
        if (ref == null) { // 不在场景
            return Optional.empty(); // 无法算距
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 分线实体表
        if (line == null) { // 分线不存在
            return Optional.empty(); // 无上下文
        }
        var self = line.entities.get(playerId); // 玩家当前坐标
        if (self == null) { // 实体缺失
            return Optional.empty(); // 无法算距
        }
        float dx = self.x - tx; // 到目标点 X 差
        float dy = self.y - ty; // 到目标点 Y 差
        float dz = self.z - tz; // 到目标点 Z 差
        return Optional.of((float) Math.sqrt(dx * dx + dy * dy + dz * dz)); // 玩家到地面点的施法距离
    }

    /** 玩家是否在 playerScene 映射中（已进场景且未 leave） */
    public boolean isPlayerInScene(long playerId) { // 玩家是否在 playerScene 映射中（已进场景且未 leave）
        return playerId > 0 && playerScene.containsKey(playerId); // playerScene 含该 playerId 即视为在场景中
    }

    /**
     * 查询与玩家同分线的目标实体类型，供 SkillService 区分敌/友/NPC。
     */
    public Optional<Integer> getEntityTypeInLine(long playerId, long entityId) { // 查询与玩家同分线的目标实体类型，供 SkillService 区分敌/友/NPC
        var ref = playerScene.get(playerId); // 查询者场景引用
        if (ref == null) { // 不在场景
            return Optional.empty(); // 无法查同线实体
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 同分线实体表
        if (line == null) { // 分线不存在
            return Optional.empty(); // 无实体表
        }
        var e = line.entities.get(entityId); // 目标 entityId
        if (e == null) { // 目标不在同线
            return Optional.empty(); // 无法判定类型
        }
        return Optional.of(e.entityType); // SkillService.validateCastTarget 区分玩家/怪物/NPC 施法目标类型
    }

    /**
     * 战斗胜利后从场景移除怪物，并向 AOI 广播 SYNC_LEAVE；按 respawn_seconds 排队刷新。
     */
    public void removeMonsterFromScene(long playerId, long enemyEntityId) {
        var ref = playerScene.get(playerId);
        if (ref == null) {
            return;
        }
        entityLock.releaseByOwner(ref.sceneId, enemyEntityId, playerId);
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId));
        if (line == null) {
            return;
        }
        SceneEntity removed = line.entities.get(enemyEntityId);
        if (removed == null || removed.entityType != ENTITY_MONSTER) {
            return;
        }
        float mx = removed.x;
        float mz = removed.z;
        Integer templateId = removed.monsterTemplateId;
        removeEntity(line, enemyEntityId);
        var notify = SyncEntityScNotify.newBuilder()
                .setSyncType(SYNC_LEAVE)
                .addLeaveEntityIds(enemyEntityId)
                .build();
        broadcastToAoiViewers(line, mx, mz, -1L, MessageId.SYNC_ENTITY_SC_NOTIFY, notify.toByteArray());
        scheduleRespawn(line, templateId, mx, mz);
    }

    /** 从当前场景移除玩家：清 playerScene、清分线实体表、广播 SYNC_LEAVE、发布 leave 事件 */
    private void removePlayerEntityEverywhere(long playerId) {
        var ref = playerScene.remove(playerId);
        moveSnapshots.remove(playerId);
        movementValidator.clearPlayer(playerId);
        if (ref == null) {
            return;
        }
        lineShardScheduler.leave(ref.sceneId, ref.lineId, playerId);
        entityDebugTrace.record(playerId, EntityDebugTrace.Phase.DESPAWN,
                "leave_scene=" + ref.sceneId, System.currentTimeMillis());
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId));
        if (line != null) {
            SceneEntity self = line.entities.get(playerId);
            float bx = self != null ? self.x : 0f;
            float bz = self != null ? self.z : 0f;
            removeEntity(line, playerId);
            var leave = SyncEntityScNotify.newBuilder()
                    .setSyncType(SYNC_LEAVE)
                    .addLeaveEntityIds(playerId)
                    .build();
            broadcastToAoiViewers(line, bx, bz, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, leave.toByteArray());
        }
        sceneEventPublisher.publishLeaveScene(playerId, ref.sceneId);
    }

    /**
     * 分线首次有玩家进入时刷怪：优先刷怪点配置，否则波次伪随机。
     */
    private void ensureMonsters(SceneLineState line, int mapId) {
        if (line.monstersSpawned) {
            return;
        }
        synchronized (line) {
            if (line.monstersSpawned) {
                return;
            }
            List<MonsterConfig> templates = configQueryService.listMonstersForMap(mapId);
            boolean anyConfiguredSpawn = templates.stream()
                    .anyMatch(mc -> mc.getSpawnX() != null && mc.getSpawnZ() != null);
            if (anyConfiguredSpawn) {
                for (MonsterConfig mc : templates) {
                    float px = mc.getSpawnX() != null
                            ? clamp(mc.getSpawnX(), 0, line.width)
                            : spawnX(mc.getId(), line.width);
                    float pz = mc.getSpawnZ() != null
                            ? clamp(mc.getSpawnZ(), 0, line.height)
                            : spawnZ(mc.getId(), line.height);
                    spawnMonsterEntity(line, mc, px, pz);
                }
            } else {
                var waves = monsterWaveSimpleFactory.createWaves(templates);
                for (var wave : waves) {
                    for (MonsterConfig mc : wave.monsters()) {
                        int waveSeed = wave.waveNo() * 1000 + mc.getId();
                        spawnMonsterEntity(line, mc, spawnX(waveSeed, line.width), spawnZ(waveSeed, line.height));
                    }
                }
            }
            line.monstersSpawned = true;
            long nid = npcEntitySeq.incrementAndGet();
            float nx = line.width / 2f + 80f;
            float nz = line.height / 2f;
            var npc = new SceneEntity(nid, ENTITY_NPC, nx, 0f, nz,
                    "向导NPC", 1, 9001, null, null);
            putEntity(line, npc);
        }
    }

    private SceneEntity spawnMonsterEntity(SceneLineState line, MonsterConfig mc, float px, float pz) {
        long eid = monsterEntitySeq.incrementAndGet();
        int baseLevel = mc.getLevel() == null ? 1 : mc.getLevel();
        int recommend = line.recommendLevel > 0 ? line.recommendLevel : baseLevel;
        // 世界等级缩放：等级展示用缩放后等级；完整 atk/hp 系数可下发给战斗服
        var scaled = worldLevelManager.scaleForSpawn(line.sceneId, 0L, recommend, baseLevel * 10, baseLevel * 100);
        int displayLevel = Math.max(1, (int) Math.round(baseLevel * scaled.atkMul()));
        var me = new SceneEntity(eid, ENTITY_MONSTER, px, 0f, pz,
                mc.getName(), displayLevel,
                mc.getModelId() == null ? 0 : mc.getModelId(), null, mc.getId());
        putEntity(line, me);
        entityDebugTrace.assign(eid);
        entityDebugTrace.record(eid, EntityDebugTrace.Phase.SPAWN,
                "template=" + mc.getId() + ",scene=" + line.sceneId, System.currentTimeMillis());
        return me;
    }

    private void scheduleRespawn(SceneLineState line, Integer templateId, float x, float z) {
        if (templateId == null) {
            return;
        }
        MonsterConfig mc = configQueryService.findMonsterById(templateId);
        if (mc == null) {
            return;
        }
        int seconds = mc.getRespawnSeconds() == null ? 30 : mc.getRespawnSeconds();
        if (seconds <= 0) {
            return;
        }
        line.pendingRespawns.add(new PendingRespawn(
                templateId, x, z, System.currentTimeMillis() + seconds * 1000L));
    }

    private void flushRespawns(SceneLineState line) {
        if (line.pendingRespawns.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<PendingRespawn> due = new ArrayList<>();
        for (PendingRespawn p : line.pendingRespawns) {
            if (p.dueAtMillis <= now) {
                due.add(p);
            }
        }
        if (due.isEmpty()) {
            return;
        }
        line.pendingRespawns.removeAll(due);
        for (PendingRespawn p : due) {
            MonsterConfig mc = configQueryService.findMonsterById(p.templateId);
            if (mc == null) {
                continue;
            }
            float px = mc.getSpawnX() != null ? clamp(mc.getSpawnX(), 0, line.width) : p.x;
            float pz = mc.getSpawnZ() != null ? clamp(mc.getSpawnZ(), 0, line.height) : p.z;
            SceneEntity spawned = spawnMonsterEntity(line, mc, px, pz);
            var notify = SyncEntityScNotify.newBuilder()
                    .setSyncType(SYNC_ENTER)
                    .addEntityList(toProto(spawned))
                    .build();
            broadcastToAoiViewers(line, px, pz, -1L, MessageId.SYNC_ENTITY_SC_NOTIFY, notify.toByteArray());
        }
    }

    /** 由 waveSeed 确定性生成 X 坐标，避免每线刷怪位置完全随机导致不可复现 */
    private static float spawnX(int seed, int width) { // 由 waveSeed 确定性生成 X 坐标，避免每线刷怪位置完全随机导致不可复现
        int w = Math.max(width, 1); // 地图宽至少 1，防除零
        return 50f + (seed * 37L % Math.max(w - 100, 1)); // 距边界 50 像素内的确定性 X
    }

    /** 由 waveSeed 确定性生成 Z 坐标 */
    private static float spawnZ(int seed, int height) { // 由 waveSeed 确定性生成 Z 坐标
        int h = Math.max(height, 1); // 地图高至少 1
        return 50f + (seed * 91L % Math.max(h - 100, 1)); // 距边界 50 像素内的确定性 Z
    }

    /** lineId=0 表示客户端未指定分线，默认进 1 线 */
    private static int resolveLineId(int requested, int defaultLines) { // lineId=0 表示客户端未指定分线，默认进 1 线
        if (requested == 0) { // 客户端未指定分线
            return 1; // 默认 1 线
        }
        return requested; // 使用客户端指定分线号
    }

    /**
     * 选择可用分线：指定线未满（硬顶）则用指定线；达软顶仍可进但记入调度器；否则在 maxLines 内找空位。
     *
     * @return 可用 lineId；全满返回 -1
     */
    private int resolveAvailableLine(MapConfig map, int requested) {
        int defaultLines = map.getDefaultLines() == null || map.getDefaultLines() < 1 ? 1 : map.getDefaultLines();
        int maxLines = Math.max(defaultLines, sceneRuntimeProperties.getMaxLinesPerMap());
        int hardCap = Math.max(1, sceneRuntimeProperties.getMaxPlayersPerLine());
        lineShardScheduler.configure(
                sceneRuntimeProperties.getSoftPlayersPerLine(),
                hardCap,
                sceneRuntimeProperties.getSoftCapBuffId());
        if (requested > 0) {
            if (requested > maxLines) {
                return -1;
            }
            LineShardScheduler.LineAdmit admit = lineShardScheduler.admit(map.getId(), requested, maxLines);
            if (admit.result() != LineShardScheduler.AdmitResult.HARD_CAP_REJECT
                    && admit.result() != LineShardScheduler.AdmitResult.INVALID_LINE
                    && countPlayersOnLine(map.getId(), requested) < hardCap) {
                return requested;
            }
        }
        int recommended = lineShardScheduler.recommendLine(map.getId(), maxLines);
        if (recommended > 0 && countPlayersOnLine(map.getId(), recommended) < hardCap) {
            return recommended;
        }
        for (int i = 1; i <= maxLines; i++) {
            if (countPlayersOnLine(map.getId(), i) < hardCap) {
                return i;
            }
        }
        return -1;
    }

    private int countPlayersOnLine(int sceneId, int lineId) {
        SceneLineState line = lineStates.get(lineKey(sceneId, lineId));
        return countPlayers(line);
    }

    private static int countPlayers(SceneLineState line) {
        if (line == null) {
            return 0;
        }
        int n = 0;
        for (SceneEntity e : line.entities.values()) {
            if (e.entityType == ENTITY_PLAYER) {
                n++;
            }
        }
        return n;
    }

    private float[] currentPlayerPos(long playerId) {
        var ref = playerScene.get(playerId);
        if (ref == null) {
            return new float[]{0f, 0f, 0f};
        }
        SceneLineState line = lineStates.get(lineKey(ref.sceneId(), ref.lineId()));
        if (line == null) {
            return new float[]{0f, 0f, 0f};
        }
        SceneEntity self = line.entities.get(playerId);
        if (self == null) {
            return new float[]{0f, 0f, 0f};
        }
        return new float[]{self.x, self.y, self.z};
    }

    private ProtocolMessage resumeRsp(
            int code, int sceneId, int lineId, float x, float y, float z,
            List<EntityInfo> entities, String resumeToken) {
        var b = ResumeSceneScRsp.newBuilder()
                .setRetcode(code)
                .setSceneId(sceneId)
                .setLineId(lineId)
                .setPosX(x)
                .setPosY(y)
                .setPosZ(z)
                .setResumeToken(resumeToken == null ? "" : resumeToken);
        if (code == RetCode.OK && entities != null) {
            entities.forEach(b::addEntityList);
        }
        return new ProtocolMessage(MessageId.RESUME_SCENE_SC_RSP, b.build().toByteArray());
    }

    /** 分线唯一键：同地图不同线实体隔离，互不可见 */
    private static String lineKey(int sceneId, int lineId) { // 分线唯一键：同地图不同线实体隔离，互不可见
        return sceneId + ":" + lineId; // lineStates/playerScene 共用键格式
    }

    /**
     * 计算进场景出生坐标：优先 entryId 伪随机点，其次客户端指定坐标（clamp 到地图内），否则地图中心。
     */
    private static float[] computeSpawnPosition(MapConfig map, EnterSceneCsReq req) { // 计算进场景出生坐标：优先 entryId 伪随机点，其次客户端指定坐标（clamp 到地图内），否则地图中心
        int w = map.getWidth(); // 地图宽度边界
        int h = map.getHeight(); // 地图高度边界
        if (req.getEntryId() != 0) { // 指定传送点 entryId
            long e = req.getEntryId(); // 入口 ID 作随机种子
            float x = 50f + (e * 13L % Math.max(w - 100, 1)); // entryId 确定性 X
            float z = 50f + (e * 29L % Math.max(h - 100, 1)); // entryId 确定性 Z
            return new float[]{x, 0f, z}; // 出生点 [x, 0, z]
        }
        if (req.getPosX() != 0f || req.getPosY() != 0f || req.getPosZ() != 0f) { // 客户端指定坐标
            return new float[]{ // 使用客户端坐标（clamp 后）
                    clamp(req.getPosX(), 0, w), // X clamp 到地图内
                    req.getPosY(), // Y 原样
                    clamp(req.getPosZ(), 0, h) // Z clamp 到地图内
            }; // EnterSceneCsReq 指定出生坐标，经 clamp 后写入 SceneEntity.x/y/z
        }
        return new float[]{w / 2f, 0f, h / 2f}; // 默认地图中心出生
    }

    /** 将坐标限制在 [min, max] 内，防止走出地图边界 */
    private static float clamp(float v, float min, float max) { // 将坐标限制在 [min, max] 内，防止走出地图边界
        return Math.min(max, Math.max(min, v)); // 坐标边界 clamp
    }

    /** 3D AOI：八叉树粗筛 + 距离精筛 + 悬崖高低差裁剪（排除自己与遭遇中怪物）。 */
    private List<EntityInfo> visibleEntities(SceneLineState line, SceneEntity observer) {
        float r2 = line.aoiRadius * line.aoiRadius;
        List<EntityInfo> list = new ArrayList<>();
        for (SceneEntity e : entitiesInAoiCells(line, observer.x, observer.y, observer.z, line.aoiRadius)) {
            if (e.entityId == observer.entityId || e.inCombat) {
                continue;
            }
            float dx = e.x - observer.x;
            float dy = e.y - observer.y;
            float dz = e.z - observer.z;
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            if (!cliffFilter.test(new float[]{observer.x, observer.y, observer.z, e.x, e.y, e.z})) {
                continue;
            }
            list.add(toProto(e));
        }
        return list;
    }

    /**
     * 当区域内实体超过阈值时，向客户端下发 VISUAL_DEGRADE 指令。
     */
    public Map<String, Object> computeVisualDegrade(long viewerId, int sceneId, int lineId) {
        var ref = playerScene.get(viewerId);
        if (ref == null || ref.sceneId != sceneId || ref.lineId != lineId) {
            return Map.of("ok", false, "error", "not_in_scene");
        }
        var line = lineStates.get(lineKey(sceneId, lineId));
        if (line == null) {
            return Map.of("ok", false, "error", "line_not_found");
        }
        var self = line.entities.get(viewerId);
        if (self == null) {
            return Map.of("ok", false, "error", "entity_not_found");
        }
        List<VisualSignificanceScheduler.EntitySignificance> sigs = new ArrayList<>();
        for (SceneEntity e : line.entities.values()) {
            if (e.entityId == viewerId || e.entityType != ENTITY_PLAYER) {
                continue;
            }
            float dx = e.x - self.x;
            float dy = e.y - self.y;
            float dz = e.z - self.z;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            sigs.add(new VisualSignificanceScheduler.EntitySignificance(
                    e.entityId, dist, e.inCombat, 100f, false,
                    visualSignificance.significanceScore(e.inCombat, 100f, false, dist)));
        }
        return visualSignificance.computeDegrade(viewerId, self.x, self.z, sigs);
    }

    private SceneLineState newLineState(MapConfig map) {
        float aoi = map.getAoiRadius() == null || map.getAoiRadius() <= 0
                ? DEFAULT_AOI_RADIUS : map.getAoiRadius();
        int grid = map.getGridSize() == null || map.getGridSize() <= 0
                ? DEFAULT_GRID_SIZE : map.getGridSize();
        int recommend = map.getRecommendLevel() == null ? 0 : map.getRecommendLevel();
        return new SceneLineState(map.getId(), map.getWidth(), map.getHeight(), aoi, grid, recommend);
    }

    private void putEntity(SceneLineState line, SceneEntity e) {
        line.entities.put(e.entityId, e);
        e.cellKey = line.aoiGrid.cellKey(e.x, e.z);
        line.aoiGrid.insert(e.entityId, e.x, e.z);
        line.aoiOctree.insert(e.entityId, e.x, e.y, e.z);
        line.components.allocate(e.entityId, e.x, e.y, e.z, 0);
    }

    private void removeEntity(SceneLineState line, long entityId) {
        SceneEntity e = line.entities.remove(entityId);
        if (e == null) {
            return;
        }
        line.aoiGrid.remove(entityId, e.cellKey);
        line.aoiOctree.remove(entityId);
        line.components.remove(entityId);
        dynamicFrequency.evict(entityId);
        moveDeltaEncoder.evict(entityId);
        aoiDeltaEncoder.evict(entityId);
    }

    private void moveEntity(SceneLineState line, SceneEntity e, float nx, float ny, float nz) {
        long oldCell = e.cellKey;
        e.x = nx;
        e.y = ny;
        e.z = nz;
        e.cellKey = line.aoiGrid.move(e.entityId, oldCell, nx, nz);
        line.aoiOctree.move(e.entityId, nx, ny, nz);
        line.components.setPosition(e.entityId, nx, ny, nz);
    }

    private void syncComponentMove(SceneLineState line, long entityId,
                                   float ox, float oy, float oz,
                                   float nx, float ny, float nz, float speed, long nowMs) {
        long dtMs = Math.max(1L, nowMs - line.lastMoveMs);
        line.lastMoveMs = nowMs;
        float dt = dtMs / 1000f;
        if (dt > 0f) {
            line.components.setVelocity(entityId, (nx - ox) / dt, (ny - oy) / dt, (nz - oz) / dt);
        }
        line.components.setPosition(entityId, nx, ny, nz);
    }

    /** 微流水线 Tick：Movement 10ms / Logic 50ms / Sync 100ms，互不阻塞。 */
    @Scheduled(fixedDelayString = "${game.scene.ecs-tick-ms:10}")
    public void sceneComponentTick() {
        long now = System.currentTimeMillis();
        for (SceneLineState line : lineStates.values()) {
            tickMicroPipeline.tickMovement(line.components, now);
        }
    }

    @Scheduled(fixedDelayString = "${game.scene.logic-tick-ms:50}")
    public void sceneLogicTick() {
        long now = System.currentTimeMillis();
        for (SceneLineState line : lineStates.values()) {
            tickMicroPipeline.tickLogic(line.components.entityIds(), (entityId, ts) -> {
                /* Buff/AI 分片占位：由 OpenWorldGameplayFacade 承担完整生态 Tick */
            }, now);
        }
    }

    @Scheduled(fixedDelayString = "${game.scene.sync-tick-ms:100}")
    public void sceneSyncTick() {
        long now = System.currentTimeMillis();
        for (SceneLineState line : lineStates.values()) {
            tickMicroPipeline.tickSync(line.components.entityIds(), (entityId, ts) -> {
                /* PhysicsStateHash 审计占位：由 PhysicsAuthorityService 异步执行 */
            }, now);
        }
    }

    private static SceneMoveCmd toSceneMoveCmd(MoveCsReq req, float x, float y, float z, long ts) {
        MovementType mt = MovementType.fromName(req.getMovementType().name());
        ActionState action = ActionState.fromName(req.getActionState().name());
        MoveIntent intent = MoveIntent.fromName(req.getMoveIntent().name());
        return new SceneMoveCmd(
                x, y, z, req.getSpeed(), ts, mt, req.getClimbableMeshId(),
                req.getMoveFlags(), req.getMountCreatureUid(), req.getGrappleNodeId(),
                req.getWallNormalX(), req.getWallNormalY(), req.getWallNormalZ(),
                0f, 0f, 0f, "", action, intent, req.getCameraYaw(), req.getClientDeltaMs());
    }

    public Map<String, Object> performanceSnapshot() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("sceneTickEngine", sceneTickEngine.stats());
        snap.put("tickMicroPipeline", tickMicroPipeline.stats());
        snap.put("broadcastFuse", broadcastFuse.stats());
        snap.put("dynamicFrequency", dynamicFrequency.stats());
        snap.put("moveDeltaEncoder", moveDeltaEncoder.stats());
        snap.put("mergedMoveAck", mergedMoveAck.stats());
        snap.put("localPositionCache", localPositionCache.stats());
        int ecsEntities = lineStates.values().stream().mapToInt(l -> l.components.size()).sum();
        snap.put("ecsEntities", ecsEntities);
        if (redisPositionBatchWriter != null) {
            snap.put("redisPositionBatch", redisPositionBatchWriter.stats());
        }
        return snap;
    }

    private List<SceneEntity> entitiesInAoiCells(SceneLineState line, float cx, float cy, float cz, float radius) {
        List<SceneEntity> out = new ArrayList<>();
        for (Long id : line.aoiOctree.queryIds(cx, cy, cz, radius)) {
            SceneEntity e = line.entities.get(id);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    /** 兼容旧 2D 调用点：Y=0 粗查。 */
    private List<SceneEntity> entitiesInAoiCells(SceneLineState line, float cx, float cz, float radius) {
        return entitiesInAoiCells(line, cx, 0f, cz, radius);
    }

    private Set<Long> aoiPlayerIds(SceneLineState line, float x, float z, long excludePlayerId) {
        return aoiPlayerIds(line, x, 0f, z, excludePlayerId);
    }

    private Set<Long> aoiPlayerIds(SceneLineState line, float x, float y, float z, long excludePlayerId) {
        float r2 = line.aoiRadius * line.aoiRadius;
        Set<Long> viewers = new HashSet<>();
        for (SceneEntity e : entitiesInAoiCells(line, x, y, z, line.aoiRadius)) {
            if (e.playerId == null || e.playerId == excludePlayerId) {
                continue;
            }
            float dx = e.x - x;
            float dy = e.y - y;
            float dz = e.z - z;
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            if (!cliffFilter.test(new float[]{x, y, z, e.x, e.y, e.z})) {
                continue;
            }
            viewers.add(e.playerId);
        }
        return viewers;
    }

    /** 向 AOI 内玩家推送；excludePlayerId=-1 表示不排除任何人。 */
    private void broadcastToAoiViewers(
            SceneLineState line, float x, float z, long excludePlayerId, int msgId, byte[] payload) {
        for (Long viewerId : aoiPlayerIds(line, x, z, excludePlayerId)) {
            playerNotificationPort.send(viewerId, msgId, payload);
        }
    }

    /** 内存 SceneEntity 转 Protobuf EntityInfo，供 ScRsp 与 SYNC notify 共用 */
    private static EntityInfo toProto(SceneEntity e) { // 内存 SceneEntity 转 Protobuf EntityInfo，供 ScRsp 与 SYNC notify 共用
        var b = EntityInfo.newBuilder() // Protobuf EntityInfo 构建器
                .setEntityId(e.entityId) // 场景唯一 entityId
                .setEntityType(e.entityType) // 1玩家/2怪物/3NPC
                .setPosX(e.x).setPosY(e.y).setPosZ(e.z) // 当前三维坐标
                .setLevel(e.level) // 实体等级（名称板/选中框）
                .setModelId(e.modelId); // 客户端模型资源 ID
        if (e.name != null) { // 玩家/怪物有名称
            b.setName(e.name); // 名称板显示
        }
        return b.build(); // 不可变 EntityInfo
    }

    /** 组装 ENTER_SCENE_SC_RSP ProtocolMessage，code=OK 时填充 sceneId/lineId/坐标/可见实体列表 */
    private static ProtocolMessage enterRsp(
            int code, int sceneId, int lineId, float x, float y, float z, List<EntityInfo> entities) { // ENTER_SCENE 响应参数
        var b = EnterSceneScRsp.newBuilder().setRetcode(code); // 设置 retcode
        if (code == RetCode.OK) { // 成功才填场景数据
            b.setSceneId(sceneId).setLineId(lineId).setPosX(x).setPosY(y).setPosZ(z).addAllEntityList(entities); // 场景/分线/坐标/可见实体
        }
        return new ProtocolMessage(MessageId.ENTER_SCENE_SC_RSP, b.build().toByteArray()); // msgId=ENTER_SCENE_SC_RSP
    }

    /** 组装 GET_CUR_SCENE_INFO_SC_RSP，code=OK 时返回当前 sceneId/lineId/坐标/同线可见实体 */
    private static ProtocolMessage curRsp(
            int code, int sceneId, int lineId, float x, float y, float z, List<EntityInfo> entities) { // GET_CUR_SCENE_INFO 响应参数
        var b = GetCurSceneInfoScRsp.newBuilder().setRetcode(code); // 设置 retcode
        if (code == RetCode.OK) { // 成功才填数据
            b.setSceneId(sceneId).setLineId(lineId).setPosX(x).setPosY(y).setPosZ(z).addAllEntityList(entities); // 当前场景快照
        }
        return new ProtocolMessage(MessageId.GET_CUR_SCENE_INFO_SC_RSP, b.build().toByteArray()); // msgId=GET_CUR_SCENE_INFO_SC_RSP
    }

    /** 组装 MOVE_SC_RSP，失败时仍返回当前服务端坐标供客户端校正 */
    private static ProtocolMessage moveRsp(int code, float x, float y, float z) { // 组装 MOVE_SC_RSP，失败时仍返回当前服务端坐标供客户端校正
        var b = MoveScRsp.newBuilder().setRetcode(code).setPosX(x).setPosY(y).setPosZ(z); // retcode+确认坐标
        return new ProtocolMessage(MessageId.MOVE_SC_RSP, b.build().toByteArray()); // msgId=MOVE_SC_RSP
    }

    /** 组装 SWITCH_LINE_SC_RSP，code=OK 时填充新分线 sceneId/lineId/出生坐标/可见实体 */
    private static ProtocolMessage switchRsp(
            int code, int sceneId, int lineId, float x, float y, float z, List<EntityInfo> entities) { // SWITCH_LINE 响应参数
        var b = SwitchLineScRsp.newBuilder().setRetcode(code); // 设置 retcode
        if (code == RetCode.OK) { // 成功才填新线数据
            b.setSceneId(sceneId).setLineId(lineId).setPosX(x).setPosY(y).setPosZ(z).addAllEntityList(entities); // 新分线视野
        }
        return new ProtocolMessage(MessageId.SWITCH_LINE_SC_RSP, b.build().toByteArray()); // msgId=SWITCH_LINE_SC_RSP
    }

    /** 组装 GET_NEARBY_ENTITIES_SC_RSP */
    private static ProtocolMessage nearbyRsp(int code, List<EntityInfo> entities) { // 组装 GET_NEARBY_ENTITIES_SC_RSP
        var b = GetNearbyEntitiesScRsp.newBuilder().setRetcode(code); // 设置 retcode
        if (code == RetCode.OK) { // 成功才填 AOI 结果
            b.addAllEntityList(entities); // 范围内 EntityInfo
        }
        return new ProtocolMessage(MessageId.GET_NEARBY_ENTITIES_SC_RSP, b.build().toByteArray()); // msgId=GET_NEARBY_ENTITIES_SC_RSP
    }

    /** 场景中怪物实体与模板信息，monsterTemplateId = monster_config.id */
    public record LocalMonsterBattleRef(
            long enemyEntityId, int sceneId, int monsterTemplateId, String name, int level, boolean inCombat) {
    }

    private record PlayerSceneRef(int sceneId, int lineId) {
    }

    private record PendingRespawn(int templateId, float x, float z, long dueAtMillis) {
    }

    /** 单条分线运行时：AoiGrid(兼容) + AoiOctree(大世界3D) + 刷怪/刷新队列 */
    private static final class SceneLineState {
        final int sceneId;
        final int width;
        final int height;
        final float aoiRadius;
        final int gridSize;
        final AoiGrid aoiGrid;
        final AoiOctree aoiOctree;
        final ConcurrentHashMap<Long, SceneEntity> entities = new ConcurrentHashMap<>();
        final CopyOnWriteArrayList<PendingRespawn> pendingRespawns = new CopyOnWriteArrayList<>();
        volatile boolean monstersSpawned;
        /** 变为空线的时间戳；0 表示仍有玩家 */
        volatile long emptySinceMillis;
        /** 地图推荐等级，供 WorldLevel 缩放 */
        final int recommendLevel;
        /** P15 ECS 组件平铺存储（每分线独立） */
        final SceneComponentStore components = new SceneComponentStore();
        volatile long lastMoveMs = System.currentTimeMillis();

        SceneLineState(int sceneId, int width, int height, float aoiRadius, int gridSize, int recommendLevel) {
            this.sceneId = sceneId;
            this.width = width;
            this.height = height;
            this.aoiRadius = aoiRadius;
            this.gridSize = gridSize;
            this.recommendLevel = Math.max(0, recommendLevel);
            this.aoiGrid = new AoiGrid(gridSize);
            float span = Math.max(width, height);
            this.aoiOctree = new AoiOctree(0f, -64f, 0f, Math.max(256f, span), 6, 12);
        }
    }

    private static final class SceneEntity {
        final long entityId;
        final int entityType;
        float x;
        float y;
        float z;
        final String name;
        final int level;
        final int modelId;
        final Long playerId;
        final Integer monsterTemplateId;
        volatile boolean inCombat;
        long cellKey;

        SceneEntity(long entityId, int entityType, float x, float y, float z,
                    String name, int level, int modelId, Long playerId, Integer monsterTemplateId) {
            this.entityId = entityId;
            this.entityType = entityType;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.level = level;
            this.modelId = modelId;
            this.playerId = playerId;
            this.monsterTemplateId = monsterTemplateId;
        }
    }
}
