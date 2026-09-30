# 大世界二游「顶尖标准」补齐说明

本文档对应产品缺口清单：探索交互、无感跨区、刷新管理、联机物权、性能演进。  
**原则**：Scene 是主场；匹配只是入口。实时战斗 AI 仅用规则/行为树，禁止 LLM。

---

## P0（上线必备）

### 1. WorldState（内置于 Scene，可拆 WorldState-Service）

| 能力 | 实现 | API |
|------|------|-----|
| 权威快照 | `WorldStateService.snapshot` | `GET /internal/scene/open-world/world-state` |
| 解谜 Bitmap | `WorldStateBitmap`（房主世界隔离，**Redis Zone 粒度，不强依赖 MySQL**） | `POST .../world-state/puzzle-bit` |
| 机关 FSM | 既有 `MechanismStateMachine` | `POST .../mechanism/activate` |
| 天气/时间 | 既有 `WorldTimeService` | `GET/POST .../time*` |
| 房主世界 | `HostWorldContext` | `POST .../world-state/host` |

采集同步模式（`RespawnPoint.SyncMode`）：

- **WORLD_SHARED**：谁先采谁得；热路径写 `LootOwnershipBitmapStore`（Redis Bitmap），定时/关服 Append 落库
- **PER_PLAYER**：每人独立 CD，互不影响

### 2. WorldLevelManager 动态数值

- 类：`WorldLevelManager`
- Spawn 时：`SceneActorService.spawnMonsterEntity` 按世界等级相对 `map.recommendLevel` 缩放展示等级
- API：`GET/POST /internal/scene/open-world/world-level`
- GM：`set_world_level`

### 3. 世界 Boss 全局 RespawnTimer + 选主

- 类：`BossRespawnTimer`（Redis SETNX + TTL **强制写入**；`tryBecomeLeader` / `tryRespawnAsLeader` 保证仅主节点刷怪）
- API：`POST .../boss/kill`、`GET .../boss/status`
- 防止多线/多节点同时击杀或重复刷出同一只世界 Boss

### 4. 联机物权与仇恨

| 规则 | 实现 |
|------|------|
| 宝箱/材料归属 | `LootOwnershipPolicy` + `LootOwnershipBitmapStore` |
| 怪物归属 | `PartyEntityOwnership`：队伍共享锁，异队不可抢 |
| 仇恨优先级 | `ThreatPriority`：ATTACKER / HOST / DAMAGE_WEIGHTED；战斗侧 `ThreatTable` |

采集带物权：`POST .../resource/collect?lootMode=PARTY_SHARED&partyIds=...`

---

## P1（体验）

### Portal 预加载 + 跨服秒切

- `PortalPreloadService`：接近 `preloadRadius` 预租槽位并签发 `issueSeamless` 票据
- `SceneBackgroundPrecreator`：匹配 type=3 成功瞬间后台预创建实例 + 轻量房间，客户端 `preloadReady=true` 时黑屏目标小于 500ms
- API：`POST .../portal/*`、`POST /internal/scene/prep/cross-dungeon`

### 断线重连保护期

- `ReconnectProtectionService`，配置 `game.scene.reconnect-protection-ms`（默认 30000）

### 多平台热更差分 + CDN 预热

- `PatchDiffPlanner` + `CdnPreheatService`

---

## P2（架构演进）— 已落地加固

### 真·水平扩展（替代进程内 Job）

| 能力 | 实现 | 清单 |
|------|------|------|
| 节点编排 | `K8sSceneAllocator` + `SceneInstancePool` | `deploy/k8s/scene/deployment-hpa.yaml` |
| Agones（可选） | Fleet / Allocation | `deploy/agones/scene-fleet.yaml` |
| Zone 热迁移 | `SceneHotMigrateService`（批量 seamless 票据） | `POST /internal/scene/migrate/zone` |

### AOI 广播风暴防御

- `AoiUpdateBatcher`：100~200ms Tick Snapshot 合并
- `AoiBroadcastStrategy`：近距 20Hz / 远距 5Hz / 视野外仅状态
- `LightweightBattleRoom`：跨服副本点对点，不经大世界 Zone
- 热力图：`GET /internal/scene/debug/grid`

### 移动反作弊深化

- `MovementPredictionValidator`：服务端权威回滚 + AntiCheat strike
- `NavMeshPathValidator`：路径阻挡拒绝穿墙

### 分线智能调度

- `LineShardScheduler`：Redis `zone:{scene}:{line}:players`；软顶 Buff / 硬顶拒绝
- 同线组队：`POST /internal/scene/line/party-pull`

### 调试可视化

- `EntityDebugTrace`：出生→移动→死亡全生命周期 TraceId
- Grafana：`deploy/observability/grafana/scene-ops-dashboard.json`

---

## P3（观测）

- `BusinessMetricsDashboard` + `/internal/scene/open-world/metrics/business`
- Prometheus：`deploy/observability/prometheus.yml`

---

## P4（二游产品玩法层）— 探索驱动力 / 人情味 / 冒险感 / 横向玩法

权威仍在 Scene；发奖产出 `grantPlans` + `idempotencyKey`，由 bag/mail 幂等兑现。

| 主题 | 实现 | API |
|------|------|-----|
| 探索-奖励循环 | `ExplorationRewardLoopService` | `POST .../explore/discover`、`GET .../explore/progress` |
| 功能奇观 | `LandmarkWonderService` | `POST .../landmark/enter`、`.../advance` |
| 区域世界状态 | `RegionImpactService` | `POST .../region/clear-camp`、`GET .../region/status` |
| 剧情分支 | `StoryBranchService` | `POST .../story/start`、`.../choose` |
| 世界偶遇 | `WorldEncounterService` | `POST .../encounter/try`、`.../force` |
| 角色探索技能 | `ExplorationSkillService` | `POST .../explore-skill/switch`、`.../sense` |
| 移动手段 | `TraverseModeService` + `MovementPredictionValidator`（GLIDE/CLIMB/SWIM/HOOK/VEHICLE） | `POST .../traverse/unlock` |
| 环境交互 | `EnvironmentInteractionService` | `POST .../env/interact` |
| 惊喜彩蛋 | `WorldSurpriseService` | `POST .../surprise/try` |
| 大世界家园 | `OpenWorldHomesteadService` | `POST .../homestead/claim` |
| 捉宠养成 | `CreatureCatchService` | `POST .../creature/catch` |
| 休闲玩法 | `LeisureActivityService` | `POST .../leisure/play` |
| 搜打撤任务 | `ExtractionMissionService` | `POST .../extract/start|scout|assault|extract` |

门面：`OpenWorldGameplayFacade`（`GET .../gameplay/status`）。

---

## P5（产品纵深）— 解谜引擎 / 分层探索 / 角色生态位 / 动态区域 / 异步社交 / 量产管线

| 主题 | 实现 | API |
|------|------|-----|
| 规则触发器 ECA | `RuleTriggerService` | `POST .../rule/fire`、`GET .../rule/list` |
| 物理状态层 | `PhysicsLayerService`（AOI 网格广播） | `POST .../physics/apply`、`GET .../physics/aoi` |
| 解谜库模板 | `PuzzleTemplateService`（≥10 种） | `GET .../puzzle/templates`、`POST .../puzzle/instantiate\|advance` |
| 收集物分级 | `CollectibleService`（宝箱/神瞳） | `POST .../collectible/collect`、`GET .../collectible/progress` |
| 区域探索度 | `RegionProgressService`（`region_progress` + 声望阈值） | `GET .../region/progress`；`GET .../gameplay/status?playerId=&regionId=` |
| 奇观入口/LOD | `LandmarkWonderService` + `WonderEntryCondition` | `GET .../landmark/lod` |
| 角色世界技 | `WorldSkillService`（移动手段绑定角色） | `POST .../world-skill/unlock-mode` |
| 生态位采集 | `EnvironmentInteractionService.PartyTalent` | `POST .../env/gather-talent` |
| 野外烹饪 | `WorldCookingService` | `POST .../cook` |
| 区域潮汐 | `RegionImpactService`（CHAOS/Decay/事件链/Boss 联动） | `POST .../region/anchor\|tick-decay\|event-chain/advance`、`GET .../region/boss-link` |
| 公共标记 | `PublicMarkService` | `POST .../mark/place\|thank` |
| 幻影残影 | `WorldEncounterService` phantom | `POST .../phantom/leave\|play` |
| 区域频道 | `RegionWorldChannelService` | `POST .../channel/broadcast-elite\|join-battle` |
| 程序化洒点 | `ProceduralPlacementService` | `POST .../placement/fill` |
| 配置热更 | `OpenWorldConfigPatchService` | `POST .../config/patch\|reload` |

门面仍为 `OpenWorldGameplayFacade`；测试：`OpenWorldP5EnhancementFlowTest`。

---

## P6（二游手感纵深）— 垂直移动 / 持续物理区 / 引导链 / 环境硬约束 / 精通反应 / 装备养成 / Boss 贡献

| 主题 | 实现 | API |
|------|------|-----|
| 移动状态机 | `SceneMoveCmd` + `MovementType`（WALK/SWIM/CLIMB/GLIDE/SWING）；协议 `MoveCsReq.movement_type` | `POST .../move/admit` |
| 物理准入 | `MovementAdmissionService`（ClimbableMeshId / IsGlidingAvailable） | 同上；攀爬下发 `staminaCostPerSec` |
| 体力阶梯 | `StaminaConsumeService`（按动作类型+持续时间扣） | `GET .../stamina` |
| 持续伤害区 | `PersistentEffectZone` + `ZoneLifecycleManager`；火→燃烧 DoT 3s/0.5s | `POST .../physics/apply`、`POST .../physics/zone/tick` |
| 引导链 | `GuidanceChainService`（起点→仙灵→风圈→宝箱 + AOI 粒子） | `POST .../guidance/start\|advance\|particles` |
| 动态宝箱品质 | `CollectibleService.DynamicLootTier`（WorldLevel + 探索度） | `POST .../collectible/collect`（带 `worldLevel`/`regionExplorationRate`） |
| CHAOS 诅咒 | `RegionImpactService` CurseLayer + `CleanseAnchor` | `POST .../region/affliction/tick`、`POST .../region/cleanse` |
| 元素精通/量 | `ElementReactionEngine` GaugeUnit + Mastery 倍率；残留减半 | battle 内 `resolve(..., gauge, mastery)` |
| 装备强化 | `EquipEnhanceService`（每 4 级副词条成长；种子+Redis 锁） | `POST /internal/bag/equip/enhance` |
| Boss 贡献分档 | `WorldEventService` 伤害+治疗/护盾折算；前 10%/30%/50% 档 | `POST .../world-events/damage\|assist\|settle` |
| MVP 广播 | `RegionWorldChannelService.broadcastBossMvp` | `POST .../channel/broadcast-mvp` |

测试：`OpenWorldP6DepthFlowTest`、`ElementReactionMasteryGaugeTest`、`EquipEnhanceServiceTest`、`WorldBossRewardTierTest`。

---

## P7（二游动作感 / 立体移动 / 世界因果 / 社交解谜 / 养成漏斗）

权威仍在 Scene + Battle；客户端表现必须经服务端窗口/射线/位掩码校验。

| 主题 | 实现 | API / 协议 |
|------|------|------------|
| 完美闪避 / 弹反 | `ReactionValidator` + `BulletTimeService`（0.1x / 1.5s，暂停非攻击者 NPC tick） | `POST .../reaction/open-window\|validate`；battle：`/internal/battle/reaction/*`；MsgId `208–211` |
| 闪避结算评分 | 完美闪避次数 → `settleScore` 掉落权重加成（每段 +2%，上限 20%） | 战斗结算 |
| 钩锁 GRAPPLE | `GrappleNodeService.admitGrapple` + `map_grapple_nodes`；射线空间哈希粗筛 | `POST .../grapple/admit`；`MovementType.GRAPPLE` |
| 载具 RIDE | `VehicleSyncService`（VehicleSyncCmd，200ms 纠偏）+ `player_vehicle` | `POST .../vehicle/board\|sync` |
| 可破坏环境 | `WorldMutabilityService`（HEAVY_ATTACK → MutationBroadcast 300m；ZSET 分帧重生） | `POST .../mutability/damage\|respawn-scan` |
| 捕捉驯服骑乘 | `CreatureCatchService`（`CATCH_CREATURE` SecureRandom；`mount` AOI 模型） | `POST .../creature/catch\|tame\|mount` |
| 多人协同解谜 | `CoopPuzzleService` + `RuleTrigger` `REQUIRE_PARTY_MEMBERS` | `POST .../coop-puzzle/press`；`POST .../rule/fire` |
| 剧情编年史 | `PlayerChronicleService`（BitMap `chronicle:{playerId}`）+ `global_flag` 热更 | `POST .../chronicle/choose`；`GET .../chronicle/global-flag` |
| 家园种植烹饪 | `HomelandService`（惰性生长；GrantPlan 随机品质；可拆端口 8997） | `POST .../homeland/plant\|harvest\|cook` |
| 空中冲刺 / 壁走 | `MoveFlags` + `AIR_DASH`/`WALL_RUN`；壁走法线夹角 &lt; 30° | `POST .../move/admit`（`moveFlags`/`wallNormal*`） |
| 突破本 | `AscensionDungeonService`（独立 SceneInstance 180s；升级加锁） | `POST .../ascension/enter\|settle` |
| 战斗重播 | `BattleReplayService`（List 上限 5000；2x/4x 回放；24h 归档 OSS） | `POST .../replay/*`；battle：`/internal/battle/replay/*` |

Schema：`docker/mysql/05-scene_db.sql`（`map_grapple_nodes` / `player_vehicle` / `creature_template` / `land_plots` / `crop_instances`）。  
Proto：`MovementType` 增补 GRAPPLE/RIDE/AIR_DASH/WALL_RUN；`MoveCsReq.move_flags` / `mount_creature_uid` / `grapple_node_id` / `wall_normal_*`；PerfectDodge/Parry 消息。  
测试：`OpenWorldP7FeelFlowTest`。

---

## P8（本轮落地）— 公会 + 深渊

详见 [`docs/guild-abyss.md`](guild-abyss.md)。

| 主题 | 实现 | API |
|------|------|-----|
| 公会组织/科技/远征 | `guild-service`（8998） | `/internal/guild/*`、`/expedition/*` |
| 深渊 3×4 连打 | `cn.itcast.demo.mymmorpg.abyss`（battle:8991） | `/internal/battle/abyss/*` |
| 聊天 roster 打通 | `InternalChatGuildController` | `/internal/chat/guild/*` |

---

## P9（本轮补齐）— 长线养成 / 立体战斗 / 生态惊喜 / 家园博弈 / 韧性手感 / 探索终局

权威仍在 Scene + Battle + Bag；客户端表现必须经服务端窗口/校验回包。

| 主题 | 实现 | API / 协议 |
|------|------|------------|
| 命座机制挂载 | `ConstellationService` + `SkillCastValidator`（读 `constellation_buff_override`） | `POST .../constellation/unlock`、`.../skill/cast-resolve`；MsgId `1210–1212` |
| 圣遗物定向锁定 | `RelicScoringService` / `RelicRandomizer`；`EquipEnhanceService.lockSubStat` | `POST /internal/bag/relic/lock`、`/relic/enhance` |
| 专武特效解耦 | `BattleTriggerService`（`special_effect_trigger`，与命座解耦） | 战斗命中钩子 `onHit` |
| 下落重击 | `FallAttackValidator`（高度/速度阈值 + 落地硬直 + 冲击波破坏） | `POST .../fall-attack/validate`；MsgId `212–213` |
| 水下物理层 | `UnderwaterPhysicsService`（`BIOME_UNDERWATER`）；`MovementAdmissionService` 突进缩 60% | `POST .../underwater/enter\|leave` |
| 动态稀有精英 | `RareEliteSpawnService`（15min≥30 杀 → `GRID_INFESTATION` + `RARE_ELITE_APPEAR`） | `POST .../rare-elite/kill`；MsgId `217` |
| 驯服跟随采集 | `CreatureUtilityService`（`harvest_affinity` 加成直入背包） | `POST .../creature/follow-harvest` |
| 家园防盗博弈 | `HomelandGuardService`（偷取≤20% / 守卫 / `DEBUFF_SPEED_DOWN` 30s） | `POST .../homeland/guard\|steal` |
| 幻影求助引导 | `WorldEncounterService.recordPuzzleFail` → `PHANTOM_GUIDE_MARK` | `POST .../phantom/puzzle-fail`；MsgId `219` |
| 韧性/霸体 | `PoiseService`（破韧处决 / Ultimate 超级装甲 / `HIT_CONFIRM`） | `POST .../poise/hit`；MsgId `214`/`216` |
| 预输入保留 | `InputBufferService`（dodge 后 +100ms；超时 `INPUT_CLEAR`） | `POST .../input-buffer/arm\|enqueue`；MsgId `215` |
| 区域觉醒 | `RegionAwakeningService`（100% → `REGION_MASTERY` + 72h +5% WorldBuff，最多 3 层） | `POST .../region/awaken`；MsgId `218` |
| 世界之核 | `WorldCoreDungeonService`（奇观全解锁 → 4 人 CoopRoom，区别深渊） | `POST .../world-core/create`、`GET .../status` |

门面：`OpenWorldGameplayFacade`；测试：`OpenWorldP9FlowTest`、`RelicLockEnhanceTest`。

---

## P10（本轮补齐）— 活物生态 / 立体动作融合 / 地貌元素连锁 / 服务器纪元 / 野外攻城与不对称 / 制造交易 / 命中手感 / 集群 AI

权威仍在 Scene + Battle；客户端表现经服务端校验下发。逻辑落在 `mmorpg-common/.../world/{ecosystem,traverse,puzzle,narrative,endgame,economy,battle,ai}`，门面 `OpenWorldGameplayFacade`，HTTP `/internal/scene/open-world/**`。

| 主题 | 实现 | API / 协议 |
|------|------|------------|
| 生态行为状态机 | `EcosystemBehaviorService`（FEED/PATROL/FLEE/SLEEP/MIGRATE，5s Tick） | `POST .../ecosystem/tick\|proximity` |
| 亲密度协助 | `AffinityService`（Redis 语义 `creature:affinity:`；寻宝/预警） | `POST .../affinity/feed\|treasure-hint\|alert`；MsgId `2200` |
| 种群承载力 | `EcoCarryingCapacity` + `ProceduralPlacementService` | 洒点 `ecoCapacity`；过杀→刷新↓ + `ECO_IMBALANCE` 作物减产 |
| 钩锁物理 | `GrapplePhysicsService`（拉拽敌人 / 摆荡踢） | `POST .../grapple/pull-enemy\|swing-kick`；MsgId `2210` |
| 载具战斗 | `VehicleCombatService`（技能槽/韧性/`EJECT_GLIDE`） | `POST .../vehicle/cast-skill\|poise-hit`；MsgId `2220` |
| 攀爬攻击 / 毁崖 | `ClimbAttackService`；`WorldMutabilityService` `DESTROY_CLIFF` | `POST .../climb/attack`；`.../mutability/damage` |
| 地貌改写 | `TerrainMutationService`（感电水池 / 冰封河面） | `POST .../terrain/overload-water\|freeze-water` |
| 弹道改写 | `ProjectileCurveService`（SWIRL 偏转 + `GAUGE_REMNANT`） | `POST .../projectile/swirl` |
| 服务器纪元 | `ServerEpochService`（`epoch_version` 全分线同步） | `POST .../epoch/bump-flag\|advance` |
| 区域拉锯 | `RegionTugOfWarService`（FACTION_DONATE / 周结算 Buff） | `POST .../faction/donate\|weekly-settle` |
| 动态过场 | `DynamicCutsceneTrigger` | `POST .../cutscene/evaluate`；MsgId `2400` |
| 野外攻城 | `SiegeWarService`（≤20 人、`PART_BREAK`、元素破盾） | `POST .../siege/create\|break-part` |
| 不对称竞技 | `AsymmetricPlayService` + `PropTransformService` | `POST .../asymmetric/hide-seek/start\|scanner` |
| 制造树 | `CraftingTreeService`（异步 + 邮件 + `CRIT_CRAFT`） | `POST .../craft/start\|complete` |
| 交易所 | `AuctionHouseService`（一口价/竞拍/5% 手续费；可拆端口 8999） | `POST .../auction/list\|buyout` |
| 命中反馈 | `HitFeedbackService`（HitStop 50~150ms + Shake） | `POST .../hit-feedback`；MsgId `2090` |
| 预表现 | `PrePlaybackService`（`PRE_IMPACT` / `ROLLBACK` &lt;50ms） | `POST .../pre-playback/start\|confirm` |
| 集群指挥 | `SquadCommanderService`（齐射/盾墙/队长死狂暴） | `POST .../squad/command` |
| 环境取用 AI | `EnvironmentUtilizationAI`（独占投掷物） | `POST .../env-ai/pickup` |

测试：`OpenWorldP10FlowTest`。

---

## P11（本轮补齐）— 队伍共鸣 / 传说状态机 / 图鉴负反馈 / 指挥标记 / 地形冷却 / 肉鸽命运卡

权威仍在 Scene + Battle；发奖产出 `grantPlans` + `idempotencyKey`。

| 主题 | 实现 | API / 协议 |
|------|------|------------|
| 队伍元素共鸣 | `TeamCompositionService` → WorldBuff | `POST .../team/resonance/refresh` |
| 传说任务状态机 | `StoryStateMachine` Idle→Talking→Escort→Combat→Reward | `POST .../story/instance/start\|choose\|advance\|complete`；MsgId `2450` |
| 区域潮汐锁 | `InstanceRegionLockService`（任务期跳过 `tickDecay`） | 随剧情 start/complete 自动锁定/恢复 |
| 生活图鉴 | `HandbookService`（`handbook:{playerId}` 语义） | `POST .../handbook/discover\|gather`；`GET .../handbook/progress` |
| 资源贫瘠 | `RegionImpactService.applyResourceBarren`（非伤害） | 过度采集触发；产出倍率↓ 24h |
| 指挥标记/集火 | `SquadCommanderService.quickMark` + `SiegeWarService.armFocusFire` | `POST .../squad/quick-mark`；MsgId `2230/2231`；部位伤 +15%/5s |
| 地形交互冷却 | `WorldMutabilityService.TerrainInteractionTracker` | `POST .../move/admit`（`BOUNCE`+region/cell）；`RetCode.TERRAIN_EXHAUSTED=170` |
| 肉鸽命运卡 | `RogueFateCardService`（探索度≥60% / 亲密度抗性；通关潮汐净化） | `POST .../rogue/start`（`region_id`）、`.../rogue/settle` |

测试：`OpenWorldP11FlowTest`。

---

## P12（性能瓶颈加固）— 并发 / GC / AOI / Tick / DB / 锁 / 冷热分离

对应七大瓶颈清单，已在 `mmorpg-common` 落地核心组件并接入 Scene 运行时。

| 瓶颈 | 实现 | 配置 / 观测 |
|------|------|-------------|
| 同步阻塞并发 | `SceneActorMailbox`（Actor 信箱）+ `BusinessBatchConsumer`（无锁批量消费）+ 既有 `DispatchThreadModel` stripe | `GET .../gameplay/status` → `performance.concurrency` |
| GC / 对象膨胀 | `MoveCmdPool`（移动指令复用）+ `PrimitiveGridStore`（long[] 网格） | `performance.gcPooling` |
| AOI 广播风暴 | `AoiDeltaEncoder`（字段级增量）+ `AoiBroadcastStrategy` 三级频次（10m/30m/50m）+ 既有 `AoiUpdateBatcher` | `AoiBroadcastStrategy.FrequencyTier`；`performance.aoi` |
| Tick 超载 | `GameplayTickSlicer`（20 片分帧，budget 5ms）+ 生态 AI 异步 `runAsyncFireAndForget` | `game.world.tick-ms`；`performance.tick` |
| DB 线程穿刺 | `PlayerWritePipeline`（50 条/1s batch）+ `AssetWalWriter`（WAL 顺序追加） | `game.player.write-pipeline-ms`；`performance.persistence` |
| 分布式锁退化 | `RedisAtomicScriptService`（Lua 原子扣费/升级/首杀）+ `WorldLevelManager.tryUpgradeWorldLevel` | 无 Redisson 重试；`performance.redisAtomic` |
| 历史数据膨胀 | `HistoricalDataRetention`（热 3 天 / 冷 7 天 OSS）+ `SimpleBloomFilter`（图鉴/神瞳）+ `BattleReplayService` 强制归档 | 256 分片表路由；`performance.bloomFilter` |

测试：`PerformanceHardeningFlowTest`、`PerformanceDeepeningFlowTest`（common）；`OpenWorldP15BusinessFlowTest`、`InternalPhysicsRpcBridgeTest`（scene-service）；`CombatPriorityRouteFilterTest`（gateway）。

### P12 性能深化（第二轮）

在首轮七大瓶颈之上，进一步落地 ECS 组件 tick、同步 LOD 增量、L1 位置缓存、写去重、RSocket 物理 RPC、Gateway 战斗快速路径与 LitePhysics 异步审计。

| 主题 | 实现 | 配置 / 观测 |
|------|------|-------------|
| ECS dirty tick | `SceneComponentStore`（HPPC `LongIntHashMap` + 平铺 `float[]`/`int[]`，每分线独立）+ `SceneTickEngine`（仅 dirty 实体，budget 5ms） | `game.scene.ecs-tick-ms`（默认 50）；`SceneActorService.performanceSnapshot()` → `ecsEntities` / `sceneTickEngine` |
| 同步 LOD / 增量 | `DynamicFrequencyService` + `MoveDeltaEncoder`（位掩码，`cameraYaw` 变化 >2° 才同步）+ `MergedMoveAckService` | `scene_protocol`：`MoveBaseState`、`MoveDeltaState`、`MoveDeltaScNotify`、`MoveAckBatchCsReq/ScRsp` |
| 位置 L1 / Redis 批写 | `LocalPositionCache`（Caffeine）+ `RedisPositionBatchWriter`（500ms pipeline） | `game.cache.position-flush-ms` |
| 写去重 / 冷热 | `DeduplicateFilter` 接入 `PlayerWritePipeline` + `ColdHotDataRouter` | `PlayerWritePipeline.stats()` → `dedupeSkipped` |
| RSocket 物理 RPC | `InternalPhysicsRsocketClient` / `InternalPhysicsRsocketServer` + `InternalPhysicsRpcBridge` | `game.rpc.physics-rsocket.enabled`（默认 false） |
| Gateway 战斗快速路径 | `CombatPriorityRouteFilter`（X-Msg-Id 1400–2000 → `/internal/scene/fast-path`）+ `InternalSceneFastPathController` | `game.gateway.combat-route.*`；`application-split.yml` → `scene-service-combat-fast` |
| LitePhysics 异步 | `LitePhysicsEngine` + `AsyncPhysicsThreadPool`；`PhysicsAuthorityService.validateHashAsyncFuture`；`GrapplePhysicsService.validateGrappleLanding` | `POST .../physics/hash/validate-async` |

依赖：父 `pom.xml` 与 `mmorpg-common` 增加 `hppc`、`caffeine`、`rsocket-core`、`rsocket-transport-netty`。

---

## P13（体验优化）— 探索便利 / 移动流畅 / 生态沉浸 / 战斗辅助 / 长线减负

权威仍在 Scene + Battle + Bag；发奖产出 `grantPlans` + `idempotencyKey`。

| 主题 | 实现 | API |
|------|------|-----|
| 探索罗盘/雷达 | `ExplorationCompassService`（探索度≥70% 解锁；探测剩余收集物/限时挑战） | `GET .../compass/status`、`POST .../compass/probe` |
| 大地图标记 | `MapMarkerService`（已收集/未收集/进行中状态 + 追踪图标） | `GET .../map/markers` |
| 探索度世界影响 | `ExplorationWorldImpactService`（解锁路径/NPC 对话/区域事件） | `GET .../explore/world-impact` |
| 区域特色移动 | `RegionalTraverseService`（滑索/气流/高速载具） | `POST .../traverse/regional/use`、`GET .../traverse/regional/list` |
| 攀爬歇脚点 | `ClimbRestPointService` + `MovementAdmissionService` 集成 | `POST .../move/admit`（命中歇脚点恢复体力） |
| 探索能力去角色绑定 | `UniversalTraversalService`（账号级核心套件 HOOK/GLIDE/CLIMB/GRAPPLE/SWIM） | `POST .../traverse/universal/grant-kit`、`GET .../traverse/universal/status` |
| 生态叙事联动 | `EcoNarrativeBridgeService`（生态状态 × 任务触发剧情） | `POST .../eco/narrative/evaluate` |
| 环境叙事 | `EnvironmentalStoryService`（场景道具无声讲故事） | `POST .../env/story/inspect` |
| 探索正向反馈 | `WorldExplorationFeedbackService`（清理营地等解锁捷径/环境美化） | `POST .../explore/feedback` |
| 辅助战斗 | `CombatAssistService`（经典/简化模式、自动连招、伤害预警、时间缓速） | `POST .../combat/assist/mode`、`POST .../combat/assist/resolve` |
| 资源自动化 | `ResourceAutomationService`（建设设施自动产出 + 一键领取） | `POST .../automation/build`、`POST .../automation/claim-all` |
| 灵活日常 | `FlexibleDailyQuestService`（体力/任意战斗/探索多路径完成 + 一键领取） | `POST .../daily/report`、`POST .../daily/claim-all` |
| 智能养成 | `BuildRecommendationService`（装备/圣遗物推荐 + 一键配置） | `GET .../build/recommend`、`POST .../build/apply` |

测试：`OpenWorldP13FlowTest`、`OpenWorldP13EdgeCaseTest`、`OpenWorldP13BusinessFlowTest`（scene-service Internal API）。

---

## P14（体验纵深补齐）— 手感延迟 / 立体惯性 / 破坏即时反馈 / 探索惊喜 / 社交沉淀 / 多端适配 / 终局变量

权威仍在 Scene + Battle；客户端可预表现，服务端通过影子状态软回滚数值，避免 Rubber-banding。

| 短板 | 实现 | API / 协议 |
|------|------|------------|
| 操控粘滞感 | `ClientPredictedActionService` + `ServerShadowService`（500ms 快照）+ `ReactionValidator.dynamicDodgeWindowMs`（Base + RTT×0.5）+ `PrePlaybackService` 软回滚 | `POST .../predict/action/start\|reconcile`；`reaction/open-window` 带 `playerRttMs` |
| Z 轴衔接断层 | `SceneMoveCmd.inheritedVelocity*` + `TraverseMomentumService`（滑翔→下落保留 70% 水平速度；摆荡伤害系数）+ `ClimbRestPointService` 歇脚攀爬跳 +30% + `PoiseService` 韧性霸体/闪避无敌帧分层 | `POST .../move/admit`（`inheritedVelocity`）；`poise/iframe\|poise-armor` |
| 破坏反馈时延 | `DeterministicMutationService`（Seed 本地碎片）+ `TerrainMutationService` 乐观改写 + `WorldMutabilityService.TerrainInteractionTracker.clientCacheCheck` | `POST .../mutability/deterministic`；`terrain:cooldown:cache` |
| 探索短视化 | `ExplorationVitalityService`（每日调查点）+ `EcoMigrationScheduler`（生态链迁移）+ `OculiResonanceService`（70% 神瞳共鸣波 5s） | `GET .../explore/vitality/daily`；`GET .../explore/oculi/resonance` |
| 社交临时感 | `CoopCampService`（4 人野外营地）+ `PhantomBorrowService`（失败 3 次借用影子）+ `SocialTokenService`（助战印记换外观） | `POST .../camp/establish\|sign`；`POST .../phantom/borrow` |
| 多端操作割裂 | `CombatAssistService.DeviceType`（手机 +30ms 闪避窗 / +15% 钩锁吸附；PC +5% 掉落）+ 软锁敌 ≤15° + `BuildRecommendationService` 连招宏（3 槽 / 2s 间隔） | `POST .../combat/assist/device`；`POST .../build/macro/*` |
| 终局重复消耗 | `AffixShuffleService`（每周 5 词缀）+ `RogueFateCardService.exchangeFateEcho` + 区域净化后 Boss 狂暴觉醒 | `GET .../rogue/weekly-affix`；`POST .../rogue/fate-echo/exchange` |

测试：`OpenWorldP14FlowTest`。

---

## P16（权威纵深加固）— 物理哈希 / 生态因果 / 多端瞄准 / 热力调度 / 联机叙事 / 伙伴幽灵 / 混沌重连 / 拍卖稳定 / 预测补偿 / 配置双缓冲

在 P10–P14 之上补齐服务端权威校验与长线调度；协议优先使用 **20xx/22xx/24xx/25xx**（勿占用 14xx 抽卡段）。

| 欠缺 | 实现 | API / 协议 |
|------|------|------------|
| 物理规则篡改 | `SceneMoveCmd.physicsStateHash` + `PhysicsAuthorityService`（500ms 对比，偏差软拉回）+ `TerrainTopologyGraph`（DESTROY_CLIFF 射线+弹药因果链） | `POST .../physics/hash/validate`；`POST .../terrain/destroy-cliff` |
| 生态负反馈断层 | `EcoGraphService`（PredatorPreyMatrix）→ `RegionImpactService.EventChain` + `RegionTugOfWarService` 初始贡献 | `POST .../ecosystem/imbalance/ripple` |
| 多端辅助瞄准 | `CombatAssistService.hitboxScale`（移动端 BOSS 弱点 ×1.15）+ `predictiveAimAssist`（SuggestedTargetAngle 阻尼吸附） | `POST .../combat/assist/aim`；`POST .../hit/verdict` |
| 探索热力调度 | `WorldExplorationBalancer` + `DynamicLootTier.heatDensityCoefficient`（冷门 ×1.5，MsgId 2260「稀有度提升」） | `GET .../explore/loot/heat-coeff`；`POST .../explore/balancer/tick` |
| 联机叙事割裂 | `CoopNarrativeProxy`（访客 WORLD_SHIFT_SHIELD MsgId 2451）+ 编年史「助人之证」 | `POST .../story/instance/start`（带 `coopRoomId`）；`POST .../coop/narrative/*` |
| 伙伴物理存在 | `CompanionGhostService`（ActorType COMPANION_GHOST / ENTITY=4）+ CompanionPathNodes（MsgId 2501）+ 体力 50% / 鼓励 Buff | `POST .../companion/spawn`；`POST .../companion/follow-path` |
| 弱网长会话 | `NetworkEmulatorProxy`（测）+ `ServerShadowService.recoverWithFastForward`（超 30s 快进和解，不直接踢） | `POST .../shadow/recover` |
| 通胀熔断 | `AuctionHouseService.PriceStabilityIndex`（Z-Score>3 需定价理由+24h 冷却）+ 跨服货架手续费 10% | `POST .../auction/list-stable` |
| 预表现视觉补偿 | `HitFeedbackService.PredictionVerdict` + `CompensateEffectId`（划痕火花）+ `MoveTrajectoryValidator` 1s 弹性缓冲 | MsgId 2090 载荷；`POST .../hit/verdict` |
| 配置热更原子性 | `OpenWorldConfigPatchService` staging 双缓冲 + `config_version`/Git SHA + 实例 `snapshot_cache` | `POST .../config/stage\|publish\|snapshot-instance` |

测试：`OpenWorldP16AuthorityFlowTest`、`OpenWorldP16EdgeCaseTest`（common）；`OpenWorldP16BusinessFlowTest`（scene Internal API 全链路）；门面 `GET .../gameplay/status` 含 `p16` 快照。

---

## P17（联机一致性加固）— 世界主权 / 局部子弹时间 / 主机迁移 / 地形 TSV / 解谜容错 / 叙事旁观

| 欠缺 | 实现 | API / 协议 |
|------|------|------------|
| 访客偷世界 | `WorldOwnershipContext` + `AccessLevel`（HOST_ONLY/GUEST_READ/ALL_SHARE）+ `CollectibleService` Host 校验；`FightContributionPoolService` Instance Loot 按 UID 分桶 | `POST .../coop/ownership/bind`；RetCode `HOST_ONLY_DENIED=172` |
| 全局子弹时间打断队友 | `BulletTimeService.startLocal`（仅触发者+锁定 Boss）+ MsgId 2090 `showVisualSlomoOnly`；逻辑帧仍 33ms | `POST .../bullet-time/local` |
| 房主断线副本白打 | `CoopRoomElectionService`（Ping+负载选举）+ `ServerShadowService.roomSnapshot`（TTL 5min）→ `HOST_TRANSFER_SC_NOTIFY=2454` | `POST .../coop/host/disconnect\|elect` |
| 地形突变丢包不一致 | `TerrainStateVector` revision；进区全量同步；移动携带 revision → `TERRAIN_STATE_MISMATCH=171` 软拉回 | `GET .../terrain/tsv`；`POST .../move/admit-tsv` |
| 解谜满员卡关 | `CoopPuzzleService` 宽松 30s 幻影补位 / 硬性态动态降员+临时 Buff；Checkpoint 断线可 Resume | `POST .../coop-puzzle/press` |
| 叙事旁观割裂 | `CoopNarrativeProxy` HostMutexLock + SyncCutscene（`UI_BLOCK_LAYER=2452` / `2453`）+ 选项仅房主；`SocialTokenService.redeemHostSpecialty` | `POST .../coop/narrative/*`；`POST .../social/token/redeem-specialty`；RetCode `CUTSCENE_MOVE_BLOCKED=173` |

测试：`OpenWorldP17CoopConsistencyFlowTest`（common）；门面 `GET .../gameplay/status` 含 `p17` 快照。

---

## P18（八大手感短板补齐）— ASM 动作分层 / 取消优先级 / 钩锁预测 / 攀爬挂边翻越 / 摄像机移动 / 冰面滑行 / 动态输入 / 采集续传

| 欠缺 | 实现 | API / 协议 |
|------|------|------------|
| ASM 粒度太粗 | `ActionState`（GROUND_IDLE/RUN、AIR_NORMAL/HEAVY、CLIMB_ATTACK、SWIM_ATTACK）+ `MovementAdmissionService.validateActionState`；空中连击 1.5s 内 3 次轻击；`AIR_COMBAT_COST` | `SceneMoveCmd.actionState`；`POST .../move/admit`；RetCode `ACTION_STATE_REJECTED=175` |
| 取消优先级缺失 | `CancelAction`（DODGE>HEAVY>NORMAL>JUMP）+ `ReactionValidator.tryCancel` + `PoiseService.resetStiffness`；`HitFeedbackService.allowCancelDuringHitStop`（Boss 重击不可取消） | `POST .../combat/cancel/try`；`POST .../input-buffer/enqueue`（`expectedCancelAction`）；RetCode `CANCEL_PRIORITY_DENIED=176` |
| 钩锁客户端先行不足 | `GrappleNodeService.predictGrapple` 即时 ACK + `PhysicsAuthorityService` 200ms 异步审计（偏差>3m 软拉回）；`GrapplePhysicsService.pullSpeedCurve` 贝塞尔拉拽 | `POST .../grapple/predict`；`POST .../grapple/audit`；RetCode `GRAPPLE_PREDICT_ACK=177` |
| Z 轴边缘过渡缺失 | `MovementType.CLIMB_HANG`（挂边 3s、`HANG_RECOVER_RATE`）+ `CLIMB_VAULT`（顶边 0.5m 翻越瞬移） | `POST .../move/admit`（movementType=CLIMB_HANG/CLIMB_VAULT）；RetCode `VAULT_SUCCESS=174` |
| 摄像机相对移动 | `SceneMoveCmd.cameraYaw` + `moveIntent`（FORWARD/BACKWARD/LEFT/RIGHT）→ `DASH_BACKWARD`；`CombatAssistService` 摄像机 ±60° 视野锥软锁 | `POST .../move/admit`；`POST .../combat/assist/soft-lock`（`cameraYawDeg`） |
| 冰面滑行物理 | `TerrainMutationService` 广播 `surfaceFriction=0.1` / `brakeDeceleration=0.5`；`TraverseMomentumService.applyFriction`；冰面拉回阈值 2.0m | `physics/hash/validate?onIceSurface=true`；proto `MoveCsReq.client_delta_ms` |
| 高/低帧率输入不一致 | `clientDeltaMs` + `InputConfidenceAnalyzer.effectiveWindowMs = 100 + delta×2`；`ServerShadowService.heartbeat` 50ms 逻辑帧序号 | `POST .../shadow/heartbeat`；move/admit 回写 `effectiveWindowMs` |
| 采集打断无续传 | `CollectibleService.startCollect/tickCollect/pauseCollect`；5s 内续传；进度>80% 自动完成；`collectedPercent` | `POST .../collectible/start\|tick\|pause` |

测试：`OpenWorldP18FeelFlowTest`、`OpenWorldP18EdgeCaseTest`（common）；`OpenWorldP18BusinessFlowTest`（scene Internal API 全链路）；门面 `GET .../gameplay/status` 含 `p18` 快照。

---

## P20（人群拥挤与战斗性能加固）— 微流水线 / 广播熔断 / Redis 热 Key / 时间戳背书 / 地形读写分离 / 双时钟 / 零 GC / 战斗 Pod 隔离

针对单场景 50+ 同屏广播风暴、高 RTT 闪避失效、Redis 热 Key 击穿与战斗 GC 毛刺，在 P12 性能深化之上落地第二轮架构级优化。

| 痛点 | 实现 | 配置 / 观测 |
|------|------|-------------|
| 逻辑帧与渲染帧挤占 | `SceneTickMicroPipeline`：MovementPipeline 10ms / LogicPipeline 50ms / SyncPipeline 100ms，互不阻塞 | `game.scene.ecs-tick-ms`（10）、`game.scene.logic-tick-ms`（50）、`game.scene.sync-tick-ms`（100）；`SceneActorService.performanceSnapshot()` → `tickMicroPipeline` |
| 广播风暴 | `BroadcastImportanceFuseService`：单节点出带宽 >512KB/s 熔断；非战斗玩家降至 2Hz 仅 Position；战斗/Boss 保持高频 | `broadcastFuse` 指标；`markCombat` / `markBoss` |
| Redis 热 Key | `LocalDamageCounter`（guild-service）：内存 ZINCRBY 合并，1s 刷盘；`RedisPositionBatchWriter` 静止 3s 跳过写入 | `game.guild.damage-flush-ms`；`STATIONARY_SKIP_MS=3000` |
| 高 RTT 闪避失效 | `ReactionValidator.CLIENT_TIMESTAMP_BUFFER_MS=100`：ClientActionTimestamp 背书，晚到 ≤100ms 仍判定成功 | `reaction/validate` 返回 `clientTimestampBacked` |
| 地形写锁阻塞主帧 | `TerrainStateVector`：`StampedLock` 乐观读 + `scheduleBump` 200ms 延迟生效（视觉即时碎岩） | `flushPendingWrites`；`validateMoveRevision` → `optimisticRead` |
| HitStop 误伤技能 CD | `DualClockService`：技能 CD / Buff 用 Wall-Clock；动画用 Game-Time；子弹时间不影响 CD 真实流逝 | `HitFeedbackService` → `skillCdUsesWallClock` |
| 战斗 GC 毛刺 | `DamageEvent` + `CombatEventRingBuffer`：环形预分配，int 状态码；投射物上限 200 复用最旧 | `combatEvents.stats()` |
| 伪分布式瓶颈 | `BattleScenePodAllocator`：OPEN_WORLD 大进程 vs BATTLE_INSTANCE 轻量 Pod（≤4/≤20 人，内存快照无 Redis） | `battleScenePods.stats()` |
| Zone 负载 | `ZoneLoadBalancer` 接入 `SceneActorService.refreshZoneDensity` | `rebalance` / `suggestNodeAssignment` |

测试：`PerformanceCrowdOptimizationFlowTest`（common）；`PerformanceCrowdOptimizationBusinessFlowTest`、`OpenWorldP14BusinessFlowTest`、`OpenWorldP15BusinessFlowTest`、`SceneActorServiceTest`（scene-service）；`LocalDamageCounterTest`、`GuildBusinessFlowTest`（guild-service）。

回归命令：

```bash
mvn -pl mmorpg-common test "-Dtest=PerformanceCrowdOptimizationFlowTest"
mvn -pl scene-service test "-Dtest=PerformanceCrowdOptimizationBusinessFlowTest"
mvn -pl guild-service test "-Dtest=LocalDamageCounterTest,GuildBusinessFlowTest"
```

---

## 资源倾斜建议

1. **优先**：K8s HPA 接真节点目录、Portal/预创建秒切、WorldState Redis 权威  
2. **其次**：AOI 批处理压测、Boss 选主、分线软硬顶  
3. **产品体验**：P4–P14 解谜/养成/韧性窗口调参、客户端预测与软回滚、钩锁点与可破坏物铺设、生态亲密度与纪元阈值运营  
4. **运营管线**：洒点编辑器前端对接 `placement/fill` + `config/patch`；勿只靠种子数据  
5. **勿过度投入**：匹配算法复杂化（匹配≠大世界）；`trade-service` 独立拆分可在交易所压测后再做
