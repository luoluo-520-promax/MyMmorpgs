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
import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.service.ConfigQueryService;
import cn.itcast.demo.mymmorpg.service.SceneEventPublisher;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import org.springframework.stereotype.Service;

import java.util.ArrayList; // AOI 命中实体、进场景可见列表等可变 Protobuf 组装
import java.util.List; // enterRsp/switchRsp 的 EntityInfo 列表参数
import java.util.Optional; // 怪物战斗引用、施法距离等可能无结果的查询
import java.util.concurrent.ConcurrentHashMap; // lineStates/playerScene 分线实体表并发读写
import java.util.concurrent.atomic.AtomicLong; // 怪物 entityId 全局自增，与玩家 ID 空间分离

/**
 * 场景 Actor 运行时：内存维护「地图:分线 -> 实体表」，地图/怪物配置来自 MySQL + ConfigQueryService 缓存。
 * 实现 BattleScenePort，供 SkillService 施法距离校验、BattleService 开战前校验怪物实体。
 */
@Service // 内存维护「地图:分线→实体表」，实现 BattleScenePort 供战斗/技能模块查怪物与距离
public class SceneActorService implements BattleScenePort { // 对外暴露场景查询端口供战斗/技能模块调用

    /** 实体类型：1=玩家，客户端据此渲染玩家模型与名称板 */
    public static final int ENTITY_PLAYER = 1; // 实体类型：1=玩家，客户端据此渲染玩家模型与名称板

    /** 实体类型：2=怪物，可被选为 BattleStartCsReq.enemyEntityId */
    public static final int ENTITY_MONSTER = 2; // 实体类型：2=怪物，可被选为 BattleStartCsReq.enemyEntityId

    /** 实体类型：3=NPC，当前刷怪流程未生成，预留扩展 */
    public static final int ENTITY_NPC = 3; // 实体类型：3=NPC，当前刷怪流程未生成，预留扩展

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

    /** 配置查询：map_config、monster_config 带 Redis/本地缓存 */
    private final ConfigQueryService configQueryService; // 配置查询：map_config、monster_config 带 Redis/本地缓存

    private final PlayerCachePort playerCachePort; // 玩家数据读端口：handleEnterScene/handleSwitchLine 时 findById 取 name/level 组装 EntityInfo

    /** 进场景策略，如副本等级门槛、活动地图开关 */
    private final ScenePolicy scenePolicy;

    /** 同分线玩家下行推送，SYNC_ENTITY_SC_NOTIFY 经此下发 */
    private final PlayerNotificationPort playerNotificationPort;

    /** 场景事件发布（RocketMQ 或 NoOp），上报 enter/move/leave 供日志与周边系统 */
    private final SceneEventPublisher sceneEventPublisher; // 场景事件发布（RocketMQ 或 NoOp），上报 enter/move/leave 供日志与周边系统

    /** 怪物波次工厂，首次进分线时按模板刷怪 */
    private final MonsterWaveSimpleFactory monsterWaveSimpleFactory; // 怪物波次工厂，首次进分线时按模板刷怪

    /** 分线运行时状态：键 "sceneId:lineId" -> 该线实体表与地图尺寸 */
    private final ConcurrentHashMap<String, SceneLineState> lineStates = new ConcurrentHashMap<>(); // 内存分线实例表，键 sceneId:lineId

    /** 玩家当前所在场景引用：playerId -> (sceneId, lineId) */
    private final ConcurrentHashMap<Long, PlayerSceneRef> playerScene = new ConcurrentHashMap<>(); // 玩家→当前分线快速索引，移动/AOI/技能距离均依赖

    /** 怪物 entityId 序列，从 5_000_000 起避免与玩家 ID 冲突 */
    private final AtomicLong monsterEntitySeq = new AtomicLong(5_000_000L); // 刷怪时递增，保证怪物 entityId 不与 playerId 重叠

    /**
     * 构造器注入配置、缓存、策略、推送、事件与刷怪工厂。
     */
    public SceneActorService(
            ConfigQueryService configQueryService,
            PlayerCachePort playerCachePort,
            ScenePolicy scenePolicy,
            PlayerNotificationPort playerNotificationPort,
            SceneEventPublisher sceneEventPublisher,
            MonsterWaveSimpleFactory monsterWaveSimpleFactory) {
        this.configQueryService = configQueryService; // 注入 map_config / monster_config 查询服务
        this.playerCachePort = playerCachePort; // 注入玩家缓存端口，进场景/切线时读取 Player 实体
        this.scenePolicy = scenePolicy; // 注入进场景策略，handleEnterScene 调用 allowEnterScene
        this.playerNotificationPort = playerNotificationPort; // 注入 WebSocket 推送端口，广播 SYNC_ENTITY_SC_NOTIFY
        this.sceneEventPublisher = sceneEventPublisher; // 注入场景事件发布器，上报 enter/move/leave/switchLine MQ
        this.monsterWaveSimpleFactory = monsterWaveSimpleFactory; // 注入刷怪波次工厂，ensureMonsters 按波次生成怪物
    }

    /**
     * 处理进场景：校验地图/分线/策略 -> 移除旧场景实体 -> 写入新分线 -> 广播 SYNC_ENTER -> 返回可见列表。
     */
    public ProtocolMessage handleEnterScene(long playerId, EnterSceneCsReq req) { // 处理进场景：校验地图/分线/策略 -> 移除旧场景实体 -> 写入新分线 -> 广播 SYNC_ENTER -> 返回可见列表
        if (playerId <= 0) { // WebSocket 会话尚未绑定有效 playerId
            return enterRsp(RetCode.PLAYER_NOT_SELECTED, 0, 0, 0, 0, 0, List.of()); // ENTER_SCENE_SC_RSP 携带 PLAYER_NOT_SELECTED
        }
        MapConfig map = configQueryService.findMapById(req.getSceneId()); // 按 sceneId 查 map_config 宽高与 defaultLines
        if (map == null) { // sceneId 在 map_config 无记录
            return enterRsp(RetCode.SCENE_NOT_FOUND, 0, 0, 0, 0, 0, List.of()); // 目标地图不存在，拒绝进场景
        }
        if (!scenePolicy.allowEnterScene(map.getId(), playerId)) { // ScenePolicy 拒绝（等级不足/副本未开等）
            return enterRsp(RetCode.INTERNAL_ERROR, 0, 0, 0, 0, 0, List.of()); // 策略拒绝，客户端显示通用错误
        }
        int lineId = resolveLineId(req.getLineId(), map.getDefaultLines()); // 0 表示客户端未指定，默认 1 线
        if (lineId < 1 || lineId > map.getDefaultLines()) { // 分线号超出地图配置上限
            return enterRsp(RetCode.INVALID_LINE, map.getId(), 0, 0, 0, 0, List.of()); // 非法分线号，INVALID_LINE
        }

        Player player = playerCachePort.findById(playerId); // 读 player 表缓存取角色名与等级
        if (player == null) { // playerId 不存在或已删号
            return enterRsp(RetCode.PLAYER_NOT_FOUND, 0, 0, 0, 0, 0, List.of()); // 角色不存在，拒绝进场景
        }

        // 若玩家已在其他场景/分线，先移除旧实体并广播 SYNC_LEAVE
        removePlayerEntityEverywhere(playerId); // 清旧分线实体，避免同一 playerId 占两条线

        float[] pos = computeSpawnPosition(map, req); // 按 entryId/客户端坐标/地图中心算出生点
        SceneLineState line = lineStates.computeIfAbsent(lineKey(map.getId(), lineId), // 懒创建分线运行时，键 sceneId:lineId
                k -> new SceneLineState(map.getWidth(), map.getHeight())); // 新分线实例，记录地图宽高供 clamp
        ensureMonsters(line); // 分线首次有人进入时刷怪到 entities 表

        var pe = new SceneEntity(playerId, ENTITY_PLAYER, pos[0], pos[1], pos[2], // 在分线 entities 表注册玩家实体，坐标为出生点 pos
                player.getName(), player.getLevel() == null ? 1 : player.getLevel(), 0, playerId, null); // entityType=玩家，modelId=0，monsterTemplateId=null
        line.entities.put(playerId, pe); // 写入分线实体表，AOI/移动/技能距离均从此表读
        playerScene.put(playerId, new PlayerSceneRef(map.getId(), lineId)); // 记录玩家当前 sceneId+lineId 快照

        var list = visibleEntities(line, playerId); // 同分线除自己外的 EntityInfo，供客户端初始化视野
        sceneEventPublisher.publishEnterScene(playerId, map.getId(), lineId); // MQ 上报进场景事件

        // 通知同分线其他玩家：有新玩家进入视野
        var notifyOthers = SyncEntityScNotify.newBuilder() // 构造 SYNC_ENTITY_SC_NOTIFY 推送体
                .setSyncType(SYNC_ENTER) // syncType=1 表示新实体进入 AOI
                .addEntityList(toProto(pe)) // 携带新玩家 EntityInfo 供他人渲染
                .build(); // 序列化前完成 Protobuf 组装
        broadcastToPlayersInLine(line, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, notifyOthers.toByteArray()); // msgId=SYNC_ENTITY_SC_NOTIFY 推同线他人

        return enterRsp(RetCode.OK, map.getId(), lineId, pe.x, pe.y, pe.z, list); // ENTER_SCENE_SC_RSP 含坐标与可见实体
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
        return curRsp(RetCode.OK, ref.sceneId, ref.lineId, self.x, self.y, self.z, visibleEntities(line, playerId)); // GetCurSceneInfoScRsp：场景 ID、分线、坐标及 AOI 内可见实体列表
    }

    /**
     * 处理移动：校验在场景内、速度、时间戳 -> 更新坐标 -> 广播 SYNC_MOVE -> 返回确认坐标。
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
        if (req.getSpeed() <= 0 || req.getSpeed() > MAX_MOVE_SPEED) { // 速度非法或超速作弊
            return moveRsp(RetCode.MOVE_REJECTED, self.x, self.y, self.z); // 拒绝移动，回传服务端当前坐标校正
        }
        long now = System.currentTimeMillis(); // 服务端权威毫秒时间戳
        // 客户端时间戳与服务器差超过 60s 视为异常包，拒绝移动防重放/改包
        if (Math.abs(now - req.getTimestamp()) > 60_000L) { // 时钟偏差超 60s 视为异常包
            return moveRsp(RetCode.MOVE_REJECTED, self.x, self.y, self.z); // 时间戳异常，拒绝并回当前坐标
        }

        float nx = clamp(req.getTargetX(), 0, line.width); // 目标 X clamp 到地图 [0,width]
        float ny = req.getTargetY(); // Y 轴高度，当前不做边界 clamp
        float nz = clamp(req.getTargetZ(), 0, line.height); // 目标 Z clamp 到地图 [0,height]

        self.x = nx; // 写入服务端确认 X，作为 AOI 与技能距离基准
        self.y = ny; // 写入确认 Y
        self.z = nz; // 写入确认 Z

        sceneEventPublisher.publishMove(playerId, ref.sceneId, nx, ny, nz); // MQ 上报移动轨迹

        var notify = SyncEntityScNotify.newBuilder() // 构造移动同步 notify
                .setSyncType(SYNC_MOVE) // syncType=3 坐标变更
                .addEntityList(toProto(self)) // 携带更新后 EntityInfo
                .build(); // 完成 Protobuf 组装
        broadcastToPlayersInLine(line, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, notify.toByteArray()); // 推同线他人更新位置

        return moveRsp(RetCode.OK, nx, ny, nz); // MOVE_SC_RSP 确认服务端坐标
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
        if (target < 1 || target > map.getDefaultLines() || target == ref.lineId) { // 非法分线或切到当前线
            return switchRsp(RetCode.INVALID_LINE, map.getId(), ref.lineId, 0, 0, 0, List.of()); // INVALID_LINE
        }

        Player player = playerCachePort.findById(playerId); // 切线后重建 EntityInfo 需 name/level
        if (player == null) { // 角色不存在
            return switchRsp(RetCode.PLAYER_NOT_FOUND, 0, 0, 0, 0, 0, List.of()); // PLAYER_NOT_FOUND
        }

        SceneLineState oldLine = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 旧分线实体表
        if (oldLine != null) { // 旧线仍存在
            oldLine.entities.remove(playerId); // 从旧线 entities 移除玩家
            var leave = SyncEntityScNotify.newBuilder() // 构造离开视野 notify
                    .setSyncType(SYNC_LEAVE) // syncType=2 实体离开
                    .addLeaveEntityIds(playerId) // 告知旧线他人此 playerId 已离开
                    .build(); // 完成组装
            broadcastToPlayersInLine(oldLine, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, leave.toByteArray()); // 旧线广播 SYNC_LEAVE
        }

        float cx = map.getWidth() / 2f; // 切线默认出生 X=地图中心
        float cz = map.getHeight() / 2f; // 切线默认出生 Z=地图中心
        SceneLineState newLine = lineStates.computeIfAbsent(lineKey(map.getId(), target), // 懒创建目标分线
                k -> new SceneLineState(map.getWidth(), map.getHeight())); // 新分线记录地图尺寸
        ensureMonsters(newLine); // 目标分线首次有人时刷怪

        var pe = new SceneEntity(playerId, ENTITY_PLAYER, cx, 0f, cz, // 在新分线 entities 表注册，出生点为地图中心
                player.getName(), player.getLevel() == null ? 1 : player.getLevel(), 0, playerId, null); // 玩家实体，Y=0 地面高度
        newLine.entities.put(playerId, pe); // 写入新分线实体表
        playerScene.put(playerId, new PlayerSceneRef(map.getId(), target)); // 更新 playerScene 指向新 lineId

        sceneEventPublisher.publishSwitchLine(playerId, map.getId(), ref.lineId, target); // MQ 上报切线 from→to

        var enterNotify = SyncEntityScNotify.newBuilder() // 新线进入视野 notify
                .setSyncType(SYNC_ENTER) // syncType=1 新实体进入
                .addEntityList(toProto(pe)) // 携带玩家 EntityInfo
                .build(); // 完成组装
        broadcastToPlayersInLine(newLine, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, enterNotify.toByteArray()); // 新线他人收到 SYNC_ENTER

        return switchRsp(RetCode.OK, map.getId(), target, pe.x, pe.y, pe.z, visibleEntities(newLine, playerId)); // SWITCH_LINE_SC_RSP 含新线视野
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
        float r = req.getRadius(); // AOI 查询半径
        if (r <= 0) { // 半径必须为正
            return nearbyRsp(RetCode.INTERNAL_ERROR, List.of()); // 非法半径
        }
        float cx = req.getCenterX(); // 圆心 X（通常客户端传自身或鼠标点）
        float cy = req.getCenterY(); // 圆心 Y
        float cz = req.getCenterZ(); // 圆心 Z
        float r2 = r * r; // 半径平方，避免开方比较
        List<EntityInfo> out = new ArrayList<>(); // 命中 AOI 的 EntityInfo 收集器
        for (var e : line.entities.values()) { // 遍历当前分线全部实体（玩家+怪物）
            float dx = e.x - cx; // X 轴偏移
            float dy = e.y - cy; // Y 轴偏移
            float dz = e.z - cz; // Z 轴偏移
            if (dx * dx + dy * dy + dz * dz <= r2) { // 三维距离平方 ≤ r² 即命中 AOI
                out.add(toProto(e)); // 转 EntityInfo 加入结果
            }
        }
        return nearbyRsp(RetCode.OK, out); // GET_NEARBY_ENTITIES_SC_RSP 返回范围内实体
    }

    /**
     * 断线或登出：解除 PushRegistry 绑定并从场景移除玩家实体、广播 SYNC_LEAVE。
     */
    public void onPlayerLeave(long playerId) { // 断线或登出：解除 PushRegistry 绑定并从场景移除玩家实体、广播 SYNC_LEAVE
        if (playerId <= 0) { // 无效 playerId 无需清理
            return; // 直接结束，避免误删
        }
        playerNotificationPort.unbind(playerId); // 解除 WebSocket→playerId 推送绑定
        removePlayerEntityEverywhere(playerId); // 从分线移除实体并广播 SYNC_LEAVE
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

    @Override // BattleScenePort.findMonsterForBattle：供 BattleService 开战前校验同分线怪物实体
    public Optional<MonsterBattleRef> findMonsterForBattle(long playerId, long enemyEntityId) { // BattleScenePort 对外怪物查询
        return findLocalMonsterForBattle(playerId, enemyEntityId) // 先查同分线内存怪物
                .map(r -> new MonsterBattleRef(r.enemyEntityId(), r.sceneId(), r.monsterTemplateId())); // 转为战斗模块 DTO
    }

    /**
     * 校验玩家当前分线内是否存在该怪物实体，供 BattleStart 前置校验。
     * monsterTemplateId 来自刷怪时写入的 monster_config.id。
     */
    public Optional<LocalMonsterBattleRef> findLocalMonsterForBattle(long playerId, long enemyEntityId) { // 校验玩家当前分线内是否存在该怪物实体，供 BattleStart 前置校验
        if (playerId <= 0) { // 未选角
            return Optional.empty(); // 无法开战
        }
        var ref = playerScene.get(playerId); // 玩家必须在场景中
        if (ref == null) { // 未进场景
            return Optional.empty(); // 无场景上下文
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 当前分线实体表
        if (line == null) { // 分线不存在
            return Optional.empty(); // 分线已失效
        }
        var e = line.entities.get(enemyEntityId); // 按 entityId 查目标实体
        if (e == null || e.entityType != ENTITY_MONSTER || e.monsterTemplateId == null) { // 非怪物或模板缺失
            return Optional.empty(); // 不能对该 entityId 开战
        }
        return Optional.of(new LocalMonsterBattleRef(e.entityId, ref.sceneId, e.monsterTemplateId, e.name, e.level)); // BattleStartCsReq 校验通过：携带怪物 templateId 供 battle-service 查 monster_config
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
     * 战斗胜利后从场景移除怪物实体，并向同分线全体广播 SYNC_LEAVE（含击杀者）。
     */
    public void removeMonsterFromScene(long playerId, long enemyEntityId) { // 战斗胜利后从场景移除怪物实体，并向同分线全体广播 SYNC_LEAVE（含击杀者）
        var ref = playerScene.get(playerId); // 击杀者场景引用，确定分线
        if (ref == null) { // 击杀者不在场景
            return; // 无需移除
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 战斗发生所在分线
        if (line == null) { // 分线不存在
            return; // 无需移除
        }
        var removed = line.entities.remove(enemyEntityId); // 从 entities 表删除被击杀怪物
        if (removed == null || removed.entityType != ENTITY_MONSTER) { // 非怪物或未找到
            return; // 移除失败或无操作
        }
        var notify = SyncEntityScNotify.newBuilder() // 怪物离开视野 notify
                .setSyncType(SYNC_LEAVE) // syncType=2
                .addLeaveEntityIds(enemyEntityId) // 告知客户端移除此 entityId
                .build(); // 完成组装
        broadcastToAllPlayersInLine(line, MessageId.SYNC_ENTITY_SC_NOTIFY, notify.toByteArray()); // 全分线（含击杀者）广播
    }

    /** 向分线内所有玩家实体（含自己）推送，用于怪物离开等全员可见事件 */
    private void broadcastToAllPlayersInLine(SceneLineState line, int msgId, byte[] payload) { // 向分线内所有玩家实体（含自己）推送，用于怪物离开等全员可见事件
        for (var e : line.entities.values()) { // 遍历分线全部实体
            if (e.playerId != null) { // 仅玩家实体有 WebSocket 推送目标
                playerNotificationPort.send(e.playerId, msgId, payload); // 向该 playerId 下发 msgId+payload
            }
        }
    }

    /** 从当前场景移除玩家：清 playerScene、清分线实体表、广播 SYNC_LEAVE、发布 leave 事件 */
    private void removePlayerEntityEverywhere(long playerId) { // 从当前场景移除玩家：清 playerScene、清分线实体表、广播 SYNC_LEAVE、发布 leave 事件
        var ref = playerScene.remove(playerId); // 原子移除玩家→分线索引，得到旧 sceneId+lineId
        if (ref == null) { // 玩家本就不在场景
            return; // 无需清理
        }
        var line = lineStates.get(lineKey(ref.sceneId, ref.lineId)); // 旧分线实体表
        if (line != null) { // 旧线仍存在
            line.entities.remove(playerId); // 从 entities 删玩家
            var leave = SyncEntityScNotify.newBuilder() // 离开视野 notify
                    .setSyncType(SYNC_LEAVE) // syncType=2
                    .addLeaveEntityIds(playerId) // 同线他人移除此 playerId
                    .build(); // 完成组装
            broadcastToPlayersInLine(line, playerId, MessageId.SYNC_ENTITY_SC_NOTIFY, leave.toByteArray()); // 推同线他人 SYNC_LEAVE
        }
        sceneEventPublisher.publishLeaveScene(playerId, ref.sceneId); // MQ 上报离场景
    }

    /**
     * 分线首次有玩家进入时刷怪：double-checked locking，按波次在地图内伪随机坐标生成怪物实体。
     */
    private void ensureMonsters(SceneLineState line) { // 分线首次有玩家进入时刷怪：double-checked locking，按波次在地图内伪随机坐标生成怪物实体
        if (line.monstersSpawned) { // 该分线已刷过怪
            return; // 跳过重复刷怪
        }
        synchronized (line) { // 分线级互斥锁，double-checked 防止并发首次进线重复刷怪
            if (line.monstersSpawned) { // double-check 已刷怪标志
                return; // 其他线程已刷完
            }
            List<MonsterConfig> templates = configQueryService.listAllMonsters(); // 读 monster_config 全表模板
            var waves = monsterWaveSimpleFactory.createWaves(templates); // 按波次分组怪物模板
            for (var wave : waves) { // 逐波刷怪
                for (MonsterConfig mc : wave.monsters()) { // 波内每只怪物
                    int waveSeed = wave.waveNo() * 1000 + mc.getId(); // 确定性种子，同线刷怪位置可复现
                    float px = spawnX(waveSeed, line.width); // 由 seed 算 X 坐标
                    float pz = spawnZ(waveSeed, line.height); // 由 seed 算 Z 坐标
                    long eid = monsterEntitySeq.incrementAndGet(); // 分配唯一怪物 entityId（≥5_000_000）
                    var me = new SceneEntity(eid, ENTITY_MONSTER, px, 0f, pz, // 在分线 entities 注册怪物，Y=0 地面
                            mc.getName(), mc.getLevel() == null ? 1 : mc.getLevel(), // 怪物名称与等级来自 monster_config
                            mc.getModelId() == null ? 0 : mc.getModelId(), null, mc.getId()); // playerId=null，monsterTemplateId=monster_config.id
                    line.entities.put(eid, me); // 写入分线实体表，可被 AOI 与开战选中
                }
            }
            line.monstersSpawned = true; // 标记该分线已完成刷怪
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

    /** 同分线除自己外的全部实体，作为进场景/切线 ScRsp 的 entityList */
    private List<EntityInfo> visibleEntities(SceneLineState line, long selfPlayerId) { // 同分线除自己外的全部实体，作为进场景/切线 ScRsp 的 entityList
        List<EntityInfo> list = new ArrayList<>(line.entities.size()); // 预分配容量，装同线他人 EntityInfo
        for (var e : line.entities.values()) { // 遍历分线全部实体
            if (e.entityId != selfPlayerId) { // 排除自己，客户端自己由 ScRsp 坐标单独渲染
                list.add(toProto(e)); // SceneEntity→EntityInfo 加入可见列表
            }
        }
        return list; // 供 ENTER_SCENE/SWITCH_LINE ScRsp.entityList
    }

    /** 向分线内其他玩家推送，excludePlayerId 为移动/进场景发起者自身 */
    private void broadcastToPlayersInLine(SceneLineState line, long excludePlayerId, int msgId, byte[] payload) { // 向分线内其他玩家推送，excludePlayerId 为移动/进场景发起者自身
        for (var e : line.entities.values()) { // 遍历分线实体
            if (e.playerId != null && e.playerId != excludePlayerId) { // 仅推其他在线玩家
                playerNotificationPort.send(e.playerId, msgId, payload); // WebSocket 下发 SYNC notify
            }
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
    public record LocalMonsterBattleRef(long enemyEntityId, int sceneId, int monsterTemplateId, String name, int level) { // 场景中怪物实体与模板信息，monsterTemplateId = monster_config.id
    }

    /** 玩家当前所在场景与分线 ID 快照 */
    private record PlayerSceneRef(int sceneId, int lineId) { // 玩家当前所在场景与分线 ID 快照
    }

    /** 单条分线运行时：地图尺寸、实体表、是否已刷怪 */
    private static final class SceneLineState { // 单条分线运行时：地图尺寸、实体表、是否已刷怪
        final int width; // 地图宽度，移动 clamp 与刷怪坐标上限
        final int height; // 地图高度，移动 clamp 与刷怪坐标上限
        final ConcurrentHashMap<Long, SceneEntity> entities = new ConcurrentHashMap<>(); // 分线内 entityId→SceneEntity，AOI/移动/战斗均读写此表
        volatile boolean monstersSpawned; // 该分线是否已完成首次刷怪

        SceneLineState(int width, int height) { // 创建分线实例时传入 map_config 宽高
            this.width = width; // 记录地图宽
            this.height = height; // 记录地图高
        }
    }

    /** 分线内单个实体：玩家或怪物的运行时状态（坐标可变，其余多为 immutable） */
    private static final class SceneEntity { // 分线内单个实体：玩家或怪物的运行时状态（坐标可变，其余多为 immutable）
        final long entityId; // 场景内唯一 ID，玩家=playerId，怪物≥5_000_000
        final int entityType; // ENTITY_PLAYER/MONSTER/NPC
        float x; // 当前 X，移动与 AOI 可变
        float y; // 当前 Y
        float z; // 当前 Z
        final String name; // 名称板显示
        final int level; // 等级显示与部分技能公式
        final int modelId; // 客户端模型 ID
        /** 玩家实体非空，怪物为 null */
        final Long playerId; // 玩家实体非空，怪物为 null，推送目标依据
        /** 怪物模板 ID（monster_config.id），玩家为 null */
        final Integer monsterTemplateId; // 怪物模板 monster_config.id，玩家为 null

        SceneEntity(long entityId, int entityType, float x, float y, float z, // 构造分线内存实体
                    String name, int level, int modelId, Long playerId, Integer monsterTemplateId) { // 玩家填 playerId，怪物填 monsterTemplateId
            this.entityId = entityId; // 场景 entityId
            this.entityType = entityType; // 实体类型枚举值
            this.x = x; // 初始 X
            this.y = y; // 初始 Y
            this.z = z; // 初始 Z
            this.name = name; // 显示名
            this.level = level; // 等级
            this.modelId = modelId; // 模型
            this.playerId = playerId; // 玩家 ID，怪物 null
            this.monsterTemplateId = monsterTemplateId; // 怪物模板 ID，玩家 null
        }
    }
}
