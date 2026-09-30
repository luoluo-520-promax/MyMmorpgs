# MyMmorpg 部署与能力说明

本项目支持两种部署模式：**单体模式（默认）** 与 **微服务模式**。

---

## 能力分层：已实现 / 演示 / 计划中

### 已实现

- 单体与微服务双模式切换（Port 远程 / Feign 命令转发）
- 内部 API HMAC（`/internal/**`）与生产密钥启动校验
- Admin 后台：IP 白名单 + HMAC/API Key、配置导入（活动/更新清单）、投诉处理、操作日志
- **分阶段热更**（`POST /admin/ops/reload`）：activity → update → quest → player 配置缓存；**发布审计**（`GET /admin/ops/publish-history`）
- **配置回滚 / Diff / 灰度 / 引用完整性**：`/admin/ops/config/*`；运营 Web 控制台 `GET /ops-console.html`
- **RBAC 角色预设**：OPS / PLANNER / CS / SUPERADMIN（`AdminRbacPresets`，可 `admin.rbac.seed-presets=true` 种子）
- **AI 双管道（物理隔离）**：详见 [`docs/ai-enhancement.md`](docs/ai-enhancement.md)
  - **管道 A · 确定性战斗 AI**：`/internal/battle/ai-teammate/*`（Boss BT / 仇恨 / AI 队友技能循环；跑在 Tick，&lt;50ms，**禁止**外部 LLM）
  - **管道 B · 生成式交互 AI**：`GET /ai/health`、`POST /internal/ai/ask`（默认 `rule-coach`）；**智能 NPC** `POST /internal/npc/dialogue`（异步线程池，超时 3s → 规则回退；可选 LLM + 短期记忆/情绪 + **结构化游戏状态 RAG 注入**）
  - 护栏：`AiCostMeter` Token 预算；计划中每玩家日限 / FAQ Redis 缓存 / 出站内容安全审核 / 私有化模型优先
- **元素反应引擎**：`ElementReactionEngine`（附着→触发→反应→倍率）；协议支持客户端预测伤害/反应与 `rollback` 回滚标记
- **体力 Resin**：`/internal/bag/resin*` 自然恢复与每日购买次数
- **装备随机词条**：`EquipRandomizer` 生成主/副词条写入 `player_bag_item.affix_blob`
- **联机房间 CoopRoom**：`/internal/hall/coop/*` 最多 4 人；世界 BOSS 房间内共斗（弱化全服 AOI 伤害广播）
- **队伍管理**：`/internal/hall/party/*`（邀请码、踢人、队长转移）
- **战令 / 月卡 / 首充双倍**：`PassService` → `/internal/shop/pass/*`（含 **自动续费 / 补偿领取 / 跨平台同步 stub**）
- **七日签到 / 月卡日领**：`SignInService` → `/internal/activity/signin/*`
- **玩家冷热分离**：`PlayerEntityCacheService` + `PlayerTimerPersistenceService`；背包 `BagHotDataService`（Redis 热数据 + 定时刷盘）
- **移动延迟补偿**：`SnapshotBuffer`（约 500ms 历史）接入场景移动，超速时先回滚校验
- **客户端预测校验**：`MovementPredictionValidator`（MOVE/DASH/技能位移差异化阈值）
- **TLog 行为日志**：`TLogEventPublisher`（默认结构化日志；`game.tlog.kafka.enabled=true` 异步 Kafka 管道）
- **日志脱敏 / 采样**：`LogDesensitizer`、`LogSamplingFilter`；`TraceContext` 跨线程 TraceId
- **MQ 死信与滞后**：`DeadLetterQueueService`、`MqLagMonitor`；熔断示例 `deploy/observability/circuitbreaker-example.yml`
- **GlobalUID**：`SnowflakeIdGenerator` / `GlobalUidGenerator`（跨服合服全局唯一）
- **跨服好友**：`CrossServerFriendService` Redis SET（`cross:friend:{id}`）
- **Admin AI 辅助策划工作台**：活动/商城/投诉/战斗周报 + 任务草案 `POST /admin/ai/quest/draft` → `AiDraftVersionStore` 审核；发布前 **Diff**（`ConfigDiffService`）+ **引用完整性**（`ReferenceIntegrityValidator`）→ 灰度/热更（`/admin/ops/config/*`、`/admin/ops/reload`），非止步于草稿 JSON
- Center 路由：`game.center.mode=local|remote`；remote 调 `GET /internal/center/plan`；跨节点签发 `session_ticket`（`SCENE_TRANSFER_REDIRECT`）
- **大世界底座**：`AoiGrid` 空间哈希 AOI；`WorldZoneManager` + **`ZoneLoadBalancer`** 按密度拆合域；**`SceneInstancePool`** + **`K8sSceneAllocator`** 跨服编排；**`SceneHotMigrateService`** Zone 热迁移；无缝交接票据；**`AoiUpdateBatcher`/`AoiBroadcastStrategy`** 广播批处理与频率分级；**`LineShardScheduler`** 软硬顶分线
- **大世界顶尖标准 P0/P1**：`WorldStateService`（机关 Bitmap + 房主世界）；`WorldLevelManager` 动态数值；`BossRespawnTimer` 全局刷新锁；采集 `WORLD_SHARED`/`PER_PLAYER`/`RANDOM_SHARE`；联机 `LootOwnershipPolicy` + `PartyEntityOwnership`；`PortalPreloadService`（含 **资源预加载清单**）；`ReconnectProtectionService` 重连 30s 保护；详见 `docs/open-world-top-tier.md`
- **热更差分 / CDN 预热 / 资源补丁链**：`PatchDiffPlanner` + `ResourcePatchChain` + `POST /internal/update/cdn/preheat`
- **网关功能号段路由**：`FunctionNumberRoutingFilter`（`X-Msg-Id` → `X-Target-Service`）
- **GeoIP 就近接入**：`GeoIpRegionResolver` + `RegionAwareRoutingFilter`（IP/国家码/经纬度 → `X-Region`/`X-Edge-Pop`/`X-Redis-Shard`）
- **Boss BT 调试**：`/internal/battle/ai-teammate/bt/*` + `ThreatTable` 仇恨表（实时战斗禁止 LLM）
- **业务大盘**：`GET /internal/scene/open-world/metrics/business`
- **世界玩法 / 轻社交**：世界 BOSS；事件临时小队；**好友助战**（多阵容/冷却/报酬/感谢评价）；**家园**（访问权限 + 装饰同步 + 小游戏/评分排行/家园商店）；**CoopRoom**（表情/快捷短语/观战/点赞送花）；跨服 4 人副本匹配（`MATCH_TYPE_CROSS_DUNGEON=3`）
- **社交关系服务** `social-service`（端口 8996）：统一关系图谱（好友/黑名单/亲密度）+ 社交成就称号头像框；订阅 `SOCIAL_EVENTS`
- **AI 推理平台** `ai-service`（端口 8995）：`AiPlatformFacade` 统一推荐/客服/NPC 长期记忆/内容校验/流失干预/MLOps；`game.ai.remote.enabled=true` 时 player/admin 经 Feign 调用；Gateway `/internal/ai/**`、`/api/player/ai/**`
- **聊天增强**：自定义频道、道具/任务/坐标超链接、举报驱动的反骚扰积分与拉黑
- **聊天举报**：`/internal/chat/report`
- **反作弊**：`AntiCheatService` + **`AntiCheatRuleEngine`** + **`ClientIntegrityChecker`**（Root/模拟器等）
- **AI 内容风控（计划中与聊天对齐）**：生成式输出须经敏感词（DFA/`ChatPolicy`）或内容安全 API；违规丢弃并审计，回退 Rule-Coach（见 `docs/ai-enhancement.md` 第四节）
- **支付渠道适配**：Apple/Google/支付宝/微信 stub + MOCK；**CSV/JSON `ChannelBillProvider`**；**`AutoReconcileScheduler`**
- **容错**：scene/hall/battle Feign `FallbackFactory` + OpenFeign circuit-breaker
- **CommandPort 扩展**：`BagCommandPort` / `BattleCommandPort` / `ActivityCommandPort` / `SkillCommandPort`
- **ArchUnit** 依赖方向守卫；**压测脚本** `perf/`（JMeter + Gatling）
- **文档**：`docs/troubleshooting.md` 排障手册；`docs/openapi.md` + Postman 集合
- 好友在线态：读 Redis `player:online:{id}`；匹配使用互补评分（等级/战力/等待时间）
- 皮肤衣柜/穿戴（`12xx`，`config/skin/SkinConfigs.json`）；挑战关卡（`13xx`）联动 `BattleService.startChallengeBattle`
- **抽卡微服务** `gacha-service`（端口 8994，可嵌入）：`14xx` 保底/十连/天井/历史；`GET /internal/gacha/probability` 概率公示；`GET /internal/gacha/audit` 审计摘要；`GAME_GACHA_REMOTE_ENABLED` 远程切换
- 肉鸽（`15xx`，战斗结算回写；局内天赋加点 `1512/1513`）
- 任务配置 JSON 驱动（`config/quest/Quests.json`）+ Admin/Internal 导入与 `reload`
- 匹配跨实例 SET NX + Lua；队列分片 + 跨 shard 配对（`game.match.cross-shard-enabled`）
- 战斗日/周统计 Redis Hash（`battle:stats:daily|weekly:*`）
- 排行榜 Redis ZSET（`rank:level` / `rank:power`），miss 回退 player 表 Top50 并回填
- 邮件附件道具：`player_mail.attachments_json` → `MailItemGrantPort` → bag 幂等发奖
- Netty TCP + UDP/KCP 双通道；KCP 写缓冲水位 + 不可写时丢弃推送背压
- 迁移票据 Redis 键 `center:migration:ticket:*`（无 Redis 回退进程内）
- 好友 / 邮件 / 任务进度 **MySQL 落库**（`player_friend` / `player_mail` / `player_quest_progress`）
- 匹配队列与状态 **Redis**（`match:queue:*` / `match:player:*` / `match:status:*`）
- 战斗状态 Redis；活跃战斗索引用 SET（**不再使用 KEYS**）
- CI：单元测试、集成测试（附带 MySQL+Redis service）、`quality-gate`（JaCoCo + SpotBugs）
- `docker compose`：MySQL 四库初始化（player/battle/activity/update）+ Redis（+ 可选 Nacos）
- 支付：沙箱 HMAC（`channel-secrets`）+ HTTP 验签适配（`verify-urls`，对接方封装官方 SDK）；生产禁用 MOCK
- 可观测性：关键路径 `log.warn` + Prometheus 计数器 + TLog

### 演示级（可用但不完整）

- 匹配：双人副本/PVP + 跨服 4 人副本（可分片 + 跨 shard）；跨服场景已接 `K8sSceneAllocator` / HPA / Agones 清单与 `SceneBackgroundPrecreator` 秒切预创建（见 `deploy/k8s/scene/`、`docs/open-world-top-tier.md`）
- 活动/战斗部分奖励数值为演示常量；世界 BOSS `grantPlans` 已产出，接 bag/mail outbox 即可履约
- 好友助战 / 家园为 Redis 持久化社交（可继续升级 MySQL 装修库）；`social-service` 统一关系图谱与成就；`SOCIAL_EVENTS` 驱动下游玩法
- 智能 NPC / 顾问（管道 B）以规则+记忆为主，LLM 为可选增强（须限流/缓存/内容审核）；Boss / AI 队友（管道 A）仅行为树，永不接入 LLM
- AOI 压测为单进程基准；集群千人同屏需分布式压测验证
- 元素反应 / 战令 / 签到 / 体力为可运行骨架，数值与运营配置表可继续产品化
- 支付渠道为生产级适配器骨架（HMAC stub），官方 SDK JAR 需替换 HTTP 占位
- RocketMQ 开发默认关闭（NoOp），生产需显式开启（见下方 MQ 清单）
- TLog Kafka 默认关闭（日志管道）；生产设 `GAME_TLOG_KAFKA_ENABLED=true` 并替换真实 KafkaTemplate
- 会话 RSA 未配置时每次启动临时生成（**生产由 ProductionSecretsValidator fail-fast**）
- Admin Web UI 为静态控制台（非完整可视化表编辑器）
- GeoIP 为前缀/国家码粗解析，生产建议 MaxMind 或边缘 GSLB

### 计划中（未实施）

- 继续拆分：player-service 去除剩余域 Maven 依赖，仅保留 Feign
- 完整 RedLock / Redisson；官方微信/支付宝/Apple/Google SDK 直连；KCP 与 Lunar 全量对齐
- 真实 Anycast GSLB、跨区域多活容灾、集群级千人同屏压测纳入 CI 硬门禁
- 战令任务配置表可视化表编辑器与真 Kafka/ES 落盘
- 全链路 mTLS + Vault 密钥轮转自动化
- 实时语音频道（可选）
- **AI 生产硬化**：每玩家 LLM 日限（如 50 次）+ 单次 ≤8K tokens；FAQ Redis 缓存；`AiContentGuard` 出站审核；采纳率/对话中断率/首包 P99 大盘；点赞点踩反馈闭环
- **私有化推理优先**：生产建议内网部署 Qwen / ChatGLM 等，避免玩家对话与资产数据默认外泄至第三方公有大模型 API（支付/充值咨询尤甚）
---

## 单体模式（默认）

- 仅启动 `player-service`（HTTP 8989）
- Maven 嵌入域模块，`game.*.remote.enabled=false`
- 适合本地开发、集成测试、小规模演示

```bash
docker compose up -d mysql redis
mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 微服务模式

各域服务独立 JAR，player-service 作网关通过 Feign 转发，域服务通过 RestTemplate 远程 Port 回调。

### 服务端口

| 服务 | HTTP 端口 | 说明 |
|------|-----------|------|
| player-service | 8989 | 客户端连接、WebSocket、玩家数据 |
| scene-service | 8981 | 场景/AOI/怪物 |
| chat-service | 8982 | 聊天 |
| bag-service | 8983 | 背包 |
| skill-service | 8984 | 技能 |
| admin-service | 8985 | 后台管理 |
| hall-service | 8986 | 大厅（好友/邮件/排行） |
| quest-service | 8987 | 任务 |
| matchmaking-service | 8988 | 匹配（含跨服组队副本） |
| shop-service | 8990 | 商城氪金货架与订单（默认可嵌入 player） |
| battle-service | 8991 | 战斗 / AI 队友 |
| activity-service | 8992 | 活动 / 世界 BOSS |
| update-service | 8993 | 客户端版本与资源更新 |
| gacha-service | 8994 | 抽卡（保底 / 概率 / 历史） |
| mmorpg-gateway | 8443（`${GATEWAY_PORT}`） | 可选 API 网关 |

### 切换步骤

1. `docker compose up -d mysql redis`（首次会创建四库并挂载 schema，详见 `docs/db-migration.md`）
2. 设置共享密钥：`INTERNAL_API_SECRET=<同一强密钥>`
3. **独立 activity-service**：`GAME_PORT_REMOTE_ENABLED=true` + `BAG_SERVICE_URL=...`（否则发奖 NoOp，生产 fail-fast）
4. **支付**：生产 `SHOP_PAYMENT_MOCK_ENABLED=false`，至少配置一个 `SHOP_PAYMENT_CHANNEL_SECRET_*`；生产建议 `SHOP_PAYMENT_PRODUCTION_VERIFY=true`
5. **P0 经济硬化**：订单双写 `shop_order`、支付事件走 `mq_outbox`、发奖/累充幂等（`grant_idempotency` / `mq_inbox`）、流水账本（`wallet_ledger` / `item_ledger`）；对账入口 `POST /internal/shop/orders/reconcile`（对比 `ChannelBillProvider` 账单差异）
6. 启动各域服务，再启动 player-service 并打开远程转发（见下方矩阵）

---

## 大世界 / 全球同服（架构底座）

| 能力 | 入口 / 类 | 说明 |
|------|-----------|------|
| 动态域网格 | `WorldZoneManager`，`GET /internal/scene/world/zones` | 按玩家密度拆合 Zone 负责 cell |
| AOI | `AoiGrid`（scene 分线内） | 空间哈希 + 距离精筛 |
| AOI 压测 | `POST /internal/scene/world/aoi/stress` | 单进程千人级查询基准 |
| 无缝交接 | `MigrationTicketService.issueSeamless` | 跨节点票据带速度/朝向/zone |
| **WorldState** | `GET /internal/scene/open-world/world-state` | 时间天气 + 资源 + 解谜 Bitmap + 房主世界 |
| **WorldLevel** | `WorldLevelManager` | Spawn 时动态攻防系数 |
| **Boss 刷新锁** | `BossRespawnTimer` | 跨线唯一 RespawnTimer |
| **Portal 预加载** | `POST .../portal/preload` | 接近边界预租槽位 |
| **重连保护** | `game.scene.reconnect-protection-ms` | 默认 30s 无敌/隐身 |
| 世界 BOSS | `/internal/activity/world-events/*` | 排期/伤害/结算 `grantPlans` |
| 事件小队 | `/internal/hall/squads/*` | 玩法驱动临时组队 |
| 好友助战 | `/internal/hall/assist/*` | 借用好友角色快照 |
| 家园拜访 | `/internal/hall/home/*` | 发布布局 / 好友参观 |
| **探索玩法层** | `/internal/scene/open-world/explore*` 等 | 探索奖励 / 奇观 / 区域影响 / 偶遇 / 移动 / 副玩法 / 搜打撤；见 `docs/open-world-top-tier.md` §P4 |
| 跨服副本匹配 | match type=`3` | 4 人带宽配对，场景 `9100+modeId` |
| 抽卡服 | `/internal/gacha/*` | 协议命令 + 概率/审计 |
| 智能 NPC（管道 B） | `POST /internal/npc/dialogue` | 异步+超时回退；RAG 注入结构化状态；LLM 可选 |
| AI 队友 / Boss BT（管道 A） | `/internal/battle/ai-teammate/*` | Tick 确定性；&lt;50ms；**禁止 LLM** |
| 就近接入 | Gateway `game.gateway.region.*` | 应用层区域头；生产叠加 GSLB |
| 功能号段路由 | `FunctionNumberRoutingFilter` | 移动走 Scene、背包走 Bag |
| 反作弊 | `AntiCheatService` | 场景移动校验 + 伤害校验 API |
| AI 内容风控 | `docs/ai-enhancement.md` §四 | 出站敏感词/审核；违规丢弃+审计（计划中落地） |
| Feign 降级 | `*FallbackFactory` | scene/hall/battle |

完整对照与优先级见 **`docs/open-world-top-tier.md`**。

**弹性伸缩建议**：scene 节点按 Zone 负载 HPA；战斗密集区（Boss 房）独立高配实例；移动同步与战斗计算逐步读写分离；Redis Sentinel / 多 AZ；跨区域只做读多写少目录同步，实时权威仍粘滞到玩家当前 Zone 节点。

**资源倾斜**：优先 Scene 水平扩容与 Portal 无感切换，而非匹配算法复杂化。

---

## 环境变量矩阵

| 变量 | 用途 | dev | test/IT | prod |
|------|------|-----|---------|------|
| `SPRING_PROFILES_ACTIVE` | Profile | `dev` | （IT 属性覆盖） | `prod` |
| `MYSQL_PASSWORD` | 数据源密码 | 可默认 `123456` | CI=`test` | **必填强密码** |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` | DB 连接 | localhost | CI service | 必填 |
| `MYSQL_DB_*` | 库名 | player/battle/activity/update | 同左 | 同左 |
| `REDIS_HOST` / `REDIS_PORT` | Redis | 127.0.0.1:6379 | CI service | 必填 |
| `INTERNAL_API_SECRET` | 内部 API HMAC | 弱默认可（enabled=false） | `test-internal-secret` 或 enabled=false | **必填，禁弱默认** |
| `GAME_INTERNAL_API_ENABLED` | 是否校验签名 | 默认关（yml） | IT 常关 | **必须 true** |
| `RPC_SIGN_KEY` | RPC 签名 | 弱默认可 | — | 有配置则必强 |
| `ADMIN_HMAC_SECRET` | 后台 HMAC | 弱默认 + auth 可关 | — | **必填** |
| `ADMIN_API_KEY` | 后台 API Key | 可选弱默认 | — | 若设则禁弱默认 |
| `ADMIN_AUTH_ENABLED` | 后台鉴权开关 | `false` | — | **必须 true** |
| `ADMIN_IP_WHITELIST_ENABLED` | IP 白名单 | 默认 true | — | 建议 true |
| `SERVER_SSL_*` / `GATEWAY_SSL_*` | TLS | 可选 | — | 启用 SSL 时密码必填 |
| `ROCKETMQ_ENABLED` | MQ | false | false | 依赖事件链路时 **true** |
| `SESSION_RSA_PRIVATE_KEY` / `SESSION_RSA_PUBLIC_KEY` | 会话 RSA | 可空（临时生成） | — | **必填固定密钥** |
| `SHOP_PAYMENT_MOCK_ENABLED` | MOCK 支付 | true | — | **false** |
| `SHOP_PAYMENT_CHANNEL_SECRET_*` | 渠道沙箱 HMAC | 可空 | — | 至少配置一个 |
| `GAME_*_REMOTE_ENABLED` | 微服务转发 | false | — | 拆分时 true |
| `*_SERVICE_URL` | 服务基址 | 见 `.env.example` | — | 集群地址 |

完整样例见仓库根目录 [`.env.example`](.env.example)。

### `game.internal-api` 行为约定

| Profile | `enabled` | `secret` |
|---------|-----------|----------|
| **dev** | `false`（跳过 Filter） | 可选弱默认，仅手动开启时用 |
| **test/IT** | 属性设 `false` 或注入测试密钥 | 不使用生产密钥 |
| **prod** | `true`（强制） | 仅环境变量，启动校验拒绝空/弱值 |

---

## 热更与发布审计（对齐 MyLunarCore）

Admin 编排分阶段热更（需权限 `ops:reload`，或持有 `import:activity` / `import:manifest`）：

```bash
# 1) 导入活动 / 清单后
curl -X POST "$ADMIN/admin/ops/reload" -H "X-Admin-User-Id: 1" -H "X-Admin-Api-Key: $ADMIN_API_KEY"

# 2) 查看发布审计
curl "$ADMIN/admin/ops/publish-history?limit=20" -H "X-Admin-User-Id: 1" -H "X-Admin-Api-Key: $ADMIN_API_KEY"
```

阶段顺序：`activity-service` → `update-service` → `player-service`（清空策划表 Redis 缓存）。各域探针：`POST /internal/ops/reload`。

## Center 多节点路由 / 玩家迁移（P1）

| 配置 | 说明 |
|------|------|
| `GAME_CENTER_MODE=local\|remote` | local=进程内目录；remote=HTTP 查远端中心 |
| `GAME_CENTER_REMOTE_BASE_URL` | 例：`http://127.0.0.1:8989`（player-service） |
| `GAME_CENTER_LOCAL_NODE_ID` | 本节点标识，用于判断是否本机迁移 |
| `GAME_CENTER_ADVERTISE_HOST/PORT` | 本机广告地址 |
| `GAME_SCENE_REGISTER_TO_CENTER` | `true` 时场景节点向 Center 注册/心跳 |
| `GAME_SCENE_MAX_PLAYERS_PER_LINE` | 单线人数上限（默认 100），满员自动开线 |
| `GAME_SCENE_MAX_LINES_PER_MAP` | 单图最大分线（默认 20） |
| `GAME_SCENE_EMPTY_LINE_TTL_MS` | 空线回收宽限（默认 300s） |
| `GAME_SCENE_RECONNECT_GRACE_MS` | 断线重连快照 TTL（默认 60s） |

- `GET /internal/center/plan?sceneId=`（兼容 `planeId`）：返回 `nodeId/nodeHost/nodePort/local`
- `POST /internal/center/ticket/consume`：body `{ "ticket": "..." }`
- `POST /internal/center/nodes/register|heartbeat|unregister`：Zone 节点生命周期
- `GET /internal/center/nodes`：查看场景目录
- 传送跨节点时 `TransferSceneScRsp.retcode=27`（`SCENE_TRANSFER_REDIRECT`），附带 `redirect_host/port/session_ticket`
- 目标节点 `EnterSceneCsReq.session_ticket` 一次性消费；断线后可用 `ResumeScene`（msgId 120/121）
- 重复登录：`KickPlayerScNotify`（msgId 15）后关闭旧连接

## Redis Sentinel（HA）

```bash
docker compose -f docker-compose.redis-sentinel.yml up -d
# 各服务加 profile：
export SPRING_PROFILES_ACTIVE=prod,redis-sentinel
export REDIS_SENTINEL_MASTER=mymmorpg-redis
export REDIS_SENTINEL_NODES=127.0.0.1:26379
```

单机 Redis 仍用根目录 `docker-compose.yml` 的 `redis` 服务。

## 网关 Sticky（账号粘滞）

Gateway 默认按 `X-Account-Id`（认证过滤器注入）或 `Authorization` / `X-Auth-Token` 哈希粘滞到同一 `player-service` 实例；无密钥时回落轮询。

## AI 助手 sidecar 契约（仅管道 B · 生成式）

> 管道 A（Boss BT / AI 队友）见 battle 内部 API，**不在此契约内，且禁止走 LLM**。架构说明见 [`docs/ai-enhancement.md`](docs/ai-enhancement.md)。

| 路径 | 说明 |
|------|------|
| `GET /ai/health` | 健康与模式（默认 `rule-coach`，`llmEnabled=false`） |
| `POST /internal/ai/ask` | 内部问答（HMAC）；body 含 `accountId`/`playerId`/`question`/`topic` |
| `POST /api/player/ai/advise` | 玩家 Token 入口 |
| `POST /internal/player/ai/advise` | 网关透传 `X-Player-Id` + `X-Account-Id` |

LLM 失败或超时时回退进程内规则建议（Rule-Coach），**不阻断**游戏服主循环。生产另需：Token 预算 / 每玩家日限 / FAQ 缓存 / 出站内容安全；优先私有化模型。

## 内部 API

- 路径前缀：`/internal/**`
- 鉴权：HMAC-SHA256（`X-Internal-Timestamp` + `X-Internal-Signature` + `X-Player-Id`；等价于 MyLunarCore 的 `X-Internal-Token` 面）
- 命令型：Protobuf `application/octet-stream`；Port 型：JSON

生产启动失败示例：

```text
生产环境必须配置非空的 INTERNAL_API_SECRET（game.internal-api.secret），且不得使用开发默认值
```

---

## 后台管理鉴权（生产样例）

```bash
export SPRING_PROFILES_ACTIVE=prod
export MYSQL_PASSWORD='<strong>'
export INTERNAL_API_SECRET="$(openssl rand -hex 32)"
export ADMIN_HMAC_SECRET="$(openssl rand -hex 32)"
export ADMIN_API_KEY="$(openssl rand -hex 16)"
export ADMIN_AUTH_ENABLED=true
export ADMIN_IP_WHITELIST_ENABLED=true
mvn -pl admin-service -am spring-boot:run
```

- 路径：`/admin/**`
- HMAC：`X-Admin-User-Id` + `X-Admin-Timestamp` + `X-Admin-Signature`
- API Key：`X-Admin-Api-Key` + `X-Admin-User-Id`
- RBAC 示例：`import:activity`、`import:manifest`、`complaint:handle`

CLI：

```bash
java -jar mmorpg-cli.jar import activity --file activity_full.json --admin-user-id 1 --api-key "$ADMIN_API_KEY"
java -jar mmorpg-cli.jar import manifest --file manifest_full.json --admin-user-id 1 --secret "$ADMIN_HMAC_SECRET"
```

---

## 微服务远程开关速查

```bash
GAME_SCENE_REMOTE_ENABLED=true
GAME_CHAT_REMOTE_ENABLED=true
GAME_BAG_REMOTE_ENABLED=true
GAME_SKILL_REMOTE_ENABLED=true
GAME_HALL_REMOTE_ENABLED=true
GAME_QUEST_REMOTE_ENABLED=true
GAME_MATCH_REMOTE_ENABLED=true
GAME_BATTLE_REMOTE_ENABLED=true
GAME_ACTIVITY_REMOTE_ENABLED=true
GAME_UPDATE_REMOTE_ENABLED=true
GAME_SHOP_REMOTE_ENABLED=true
# 各域服务：
GAME_PORT_REMOTE_ENABLED=true
PLAYER_SERVICE_URL=http://127.0.0.1:8989
# 独立 shop-service 时 admin/player 均指向 8990
SHOP_SERVICE_URL=http://127.0.0.1:8990
```

---

## 可观测性 / 网关 / MQ / 质量门禁

- 指标：`/actuator/prometheus`（`mmorpg.dispatch.*`、`mmorpg.mq.*`、`mmorpg.shop.*`）
- **AI 专属（见 `docs/ai-enhancement.md`）**：`AiMetrics` / `AiCostMeter`（BT 耗时、LLM 成功率、Token 消耗）；建议补充建议采纳率、对话中断率（超时回退）、管道 B 首包 P99
- 网关：默认单体路由；`--spring.profiles.active=split` 按域拆分
- RocketMQ：开发默认关闭；生产若走最终一致事件须 `ROCKETMQ_ENABLED=true`
- **必须 MQ 的事件清单（开启后勿静默降级）**：
  - 商店订单已支付 → 活动充值进度（`SHOP_EVENTS` / activity 消费者）
  - 战斗结束 → 活动胜利次数 / 日常任务进度（`BATTLE_EVENTS` / `end`；activity + quest 消费者；Inbox 幂等；另有 HTTP 投影兜底）
- Nacos：Compose 默认 `NACOS_AUTH_ENABLE=false` **仅适合本机**；共享环境务必开认证
- 质量门禁（`quality-gate` 依赖 unit + integration 全绿；SpotBugs `failOnError=true`；JaCoCo 行覆盖率 ≥10%）：

```bash
mvn verify -Pquality-gate
mvn verify sonar:sonar   # 需自建 SonarQube
```
