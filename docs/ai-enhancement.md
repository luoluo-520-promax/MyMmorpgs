# AI 能力补强落地说明

本文档对应评审中的「补充建议 / 优化建议 / 可观测性 / 数据闭环 / 测试」条目，描述当前仓库已落地的**可运行骨架**（规则引擎 + 接口 + 指标），以及后续可替换为真实 ML/LLM 的扩展点。

> **核心原则**：确定性战斗 AI 与生成式交互 AI **物理隔离**——两条独立管道，禁止混用线程模型与依赖边界。

---

## 〇、架构分层：确定性 AI vs 生成式 AI（物理隔离）

新人最易混淆之处：Boss 行为树、AI 队友指挥与「智能 NPC / 玩家顾问」同属「AI」字样，但运行约束完全不同。文档与实现统一按下列两条管道划分。

```
┌─────────────────────────────────────────────────────────────┐
│ 管道 A · 实时战斗 AI（确定性）                                 │
│  Boss BT / AI 队友仇恨与技能循环                               │
│  宿主：游戏主循环 Tick · 延迟目标 < 50ms                       │
│  禁止：外部 LLM API、同步 HTTP、阻塞 I/O                       │
│  服务：battle-service（/internal/battle/ai-teammate/*）        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ 管道 B · 交互式 AI（生成式，可选）                              │
│  玩家顾问 / 智能 NPC 对话                                     │
│  宿主：异步非阻塞线程池 · 超时（默认 3s）→ 立即回退 Rule-Coach │
│  允许：LLM sidecar / 私有化模型 · 绝不可卡住玩家操作            │
│  服务：player-service（/internal/ai/ask）· scene NPC dialogue  │
└─────────────────────────────────────────────────────────────┘
```

| 维度 | 管道 A · 确定性 | 管道 B · 生成式 |
|------|-----------------|-----------------|
| 典型能力 | Boss BT、仇恨表、AI 队友策略 | 顾问问答、智能 NPC 闲聊 |
| 调度 | 战斗 Tick / 帧同步 | 独立线程池 + Future 超时 |
| 延迟 | **&lt; 50ms**（硬约束） | 软目标；**3s** 超时即降级 |
| 外部 API | **绝对禁止** | 可选；失败/超时 → `rule-coach` |
| 降级 | 默认即规则/BT，无「再试 LLM」 | LLM 关闭或失败时规则引擎 |
| 落地 | `AiTeammateService`、BT JSON 热更 | `NpcDialogueService`、`POST /internal/ai/ask` |

**反模式（禁止）**：在 Tick 内同步调用 LLM；把 Boss 决策结果拼进玩家可见对话 Prompt 却反向让 LLM 驱动 Tick；用同一连接池/同一超时配置服务两条管道。

---

## 一、新增能力

| 建议项 | 落地位置 | 说明 |
|--------|----------|------|
| 动态难度调节 (DDA) | `mmorpg-common/.../dda/DifficultyEvaluator` + `battle-service/.../dda/DynamicDifficultyService` | 基于 DPS/生存率/通关时间算技能频率、小怪数、奖励倍率；Redis 存历史统计；含 A/B 变体 `dda_algo` |
| 流失/付费预测 | `mmorpg-common/.../analytics/PlayerRiskScorer` + `admin-service` `AdminPlayerPredictionService` / `AdminAiPredictionController` | 规则评分占位，可换 XGBoost；输出 `suggestedAction` 供 activity 联动 |
| 行为异常检测 | `mmorpg-common/.../anticheat/BehaviorAnomalyDetector` | 点击频率/技能间隔 CV/位移抖动；已接入 `AntiCheatService.checkBehaviorAnomaly` |
| 智能匹配增强 | `MatchCompatibilityScorer.Profile` | 胜率接近、职业互补、风格偏好 |
| NLG 闲聊（管道 B） | 沿用 `NpcDialogueService` | LLM 可选 + 规则回退；严格超时见 scene `game.npc-ai` |

---

## 二、成本与限流（生产级护栏）

仅「LLM 关闭时回退规则」不够；生成式管道必须有成本护栏，防止恶意刷接口导致账单爆炸。

| 护栏 | 要求 | 现状 / 落点 |
|------|------|-------------|
| **Token 预算** | 进程/集群日预算；超限熔断 LLM，只走 Rule-Coach | 已有 `AiCostMeter` + `AiServiceHealthIndicator`（`token_budget_exceeded`） |
| **每玩家日限** | 建议 **50 次/玩家/日**（可配置） | 计划中：Redis 计数键 `ai:llm:daily:{playerId}` |
| **上下文窗口** | 单次 Prompt **≤ 8K tokens**（截断记忆 + 结构化状态优先） | `LlmHttpClient` 粗估 token；需在调用前强制截断 |
| **FAQ 缓存** | 高频通用问（如「怎么升级快？」）先查 Redis 预设答，**不调 LLM** | 计划中：`ai:faq:{hash(normalizedQ)}` TTL |
| **超时降级** | 默认 **3s**，retry **≤ 2**，失败立即规则回退 | 已有：`AiModelClient` / `LlmHttpClient` |

原则：**缓存命中 → 预算检查 →（异步）LLM → 内容安全 → 返回**；任一步失败都不得阻塞主玩法线程。

---

## 三、RAG：结构化游戏上下文注入（非裸对话历史）

智能 NPC「短期记忆 / 情绪」说明的是会话态；**玩家游戏状态**必须由系统在调用 LLM 前自动拼接，而不是把原始聊天记录整段扔给大模型。

### 推荐 Prompt 组装顺序

1. **人设与世界观**（固定 system：NPC 身份、禁区话术）
2. **结构化游戏状态**（由服务端查询后模板化注入，非客户端可信）
3. **短期记忆摘要**（情绪/最近 N 轮压缩摘要，受 8K 窗口截断）
4. **本轮玩家原话**

### 注入模板示例

```text
你是蒙德城的凯瑟琳。
【游戏状态】玩家等级=15；未完成任务=风起鹤归(questId=…); 背包相关道具=北地烟熏鸡×3；所在位置=蒙德城广场。
【情绪/记忆摘要】……
玩家对你说：「你好」。
请结合游戏世界观给出简短指引（≤80 字），勿泄露未公开剧情与数值。
```

实现约束：

- 状态字段来自权威服（quest / bag / scene），**禁止**信任客户端自报等级/背包。
- 涉及支付/充值咨询时，优先规则模板或人工客服入口，避免把资产明细送入第三方模型（见第七节）。

---

## 四、AI 内容风控（与反作弊并列）

投诉举报处理的是玩家 UGC；**模型生成文本**在返回客户端前必须过安全层。

建议在「反作弊与客户端完整性」旁增加 **AI 内容风控** 侧栏：

| 步骤 | 说明 |
|------|------|
| 1. 敏感词过滤 | DFA / 现有 `ChatPolicy` 词表可复用或独立 AI 词表 |
| 2. 可选二次审核 | 内容安全 API（超时则按「拒绝输出」处理，不裸传） |
| 3. 违规处置 | **丢弃生成结果**，回退 Rule-Coach 或固定安全话术；写审计日志（`AI_DECISION` / TLog） |
| 4. 与聊天举报衔接 | 玩家对 AI 回复举报 → 进 Admin 投诉流，可反查 Prompt 版本与模型版本 |

现状：聊天侧已有 `ChatPolicy` 敏感词；生成式 AI 出站审核为**必须补齐**的生产项（计划中与聊天策略对齐或独立 `AiContentGuard`）。

---

## 五、Admin AI 辅助策划工作台（草稿 → 校验 → 灰度闭环）

不止步于「生成草稿」，闭环为：

```
AI 生成 JSON 草稿
    → Diff 对比线上版本（高亮差异）
    → 引用完整性校验（奖励道具 ID / 任务 ID 等必须存在于配置表）
    → 策划确认（DRAFT→SUBMITTED→APPROVED）
    → 一键灰度发布到测试区（无需手工复制粘贴）
    → 全量 / 回滚（既有 /admin/ops/config/*）
```

| 环节 | 落点 |
|------|------|
| 生成 | `POST /admin/ai/quest/draft` 等；`AiDraftVersionStore` |
| Diff | `ConfigDiffService` · `/admin/ops/config/*` |
| 引用完整性 | `ReferenceIntegrityValidator`（导入与发布前） |
| 审核状态机 | `AiDraftVersionStore`：DRAFT→SUBMITTED→APPROVED→PUBLISHED |
| 灰度 / 热更 | `ConfigRollbackService` 灰度比例；`POST /admin/ops/reload` |

故事线增强：「策划上线新活动」= AI 草案 → Diff/完整性 → 测试区灰度 → 观察指标 → 全量，而不是停在「有个草稿 JSON」。

---

## 六、质量与运维（含 AI 专属可观测性）

| 建议项 | 落地位置 |
|--------|----------|
| LLM 超时/重试/成本/降级 | `LlmHttpClient` + `AiCostMeter`；`AiModelClient` 默认 timeout **3s**、retry **2**、token 预算 |
| Boss BT 热更 | Admin `POST /admin/ai/ops/bt/load` 代理 battle 内部 API；可视化编辑器仍为外部工具导出 JSON |
| AI 队友策略 | `TeammateStrategy` + `AiTeammateService.setStrategy`（CONSERVATIVE/AGGRESSIVE/SUPPORT） |
| NPC 记忆持久化 | `NpcDialogueMemory` → Redis `scene:npc:mood:*`，TTL 15min |
| 草稿审核流 | `AiDraftVersionStore`：DRAFT→SUBMITTED→APPROVED→PUBLISHED |
| AI 决策日志 | `AiDecisionLogger` + TLog `AI_DECISION` |
| 健康检查/熔断信号 | `AiServiceHealthIndicator`（成功率、BT 延迟、Token 预算） |
| AI 指标（进程内） | `AiMetrics`：BT 耗时、LLM 成功率、NPC P99 估计、决策 QPS |
| A/B | `ExperimentAssigner` 稳定分桶 |
| 训练埋点 / 模型版本 | `TrainingDataCollector` + `ModelVersionRegistry` |

### AI 专属监控指标（建议纳入可观测性大盘）

| 类别 | 指标 | 用途 |
|------|------|------|
| 业务 | **建议采纳率**（按建议接任务 / 购买道具） | 衡量顾问价值 |
| 业务 | **对话中断率**（超时回退 Rule-Coach 次数 / 总对话） | 发现 LLM 不稳或超时过紧 |
| 性能 | **P99 首字/首包延迟**（管道 B） | 体验与容量 |
| 性能 | **Token 消耗速率**（`AiCostMeter`） | 成本告警 |
| 管道 A | **BT Tick 耗时 P99**（须 &lt; 50ms） | 战斗卡顿归因 |
| 计划中 | 点赞 / 点踩反馈 | 离线微调或 Prompt 迭代 |

战斗日/周统计（`battle:stats:*`）不替代上述 AI 质量指标；二者应分面板展示。

---

## 七、私有化部署与数据隐私

| 项 | 建议 |
|----|------|
| 生产模型位置 | **优先内部开源模型私有化部署**（如 Qwen / ChatGLM），游戏服只访问内网推理服务 |
| 禁止默认外泄 | 玩家对话、任务进度、背包/资产、支付与充值相关咨询 **不得默认**打到第三方公有大模型 API |
| 若必须用公有 API | 脱敏（去账号/订单号/精确资产）、合同与数据驻留评估、独立密钥与审计；充值类话术优先规则/人工 |
| 管道 A | 与模型部署无关——战斗 Tick **永不**依赖任何模型服务可达性 |

现状：默认 `rule-coach`、LLM 关闭；开启 LLM 时仍须按上表做部署选型（**计划中**在运维清单中固化私有化优先）。

---

## 八、主要 API

### 既有

- `POST /internal/battle/dda/record` · `GET /internal/battle/dda/evaluate`（含 `strategyTier`）
- `POST /internal/battle/ai-teammate/strategy` · BT 调试 `/internal/battle/ai-teammate/bt/*`
- `POST /internal/ai/ask` · `POST /api/player/ai/advise`（管道 B · player-service）
- `POST /internal/npc/dialogue`（管道 B · scene-service；已联动长期情感记忆）
- `POST /admin/ai/prediction/{playerId}`
- `POST /admin/ai/ops/bt/load` · `/admin/ai/ops/drafts` 审核链路
- 配置闭环：`/admin/ops/config/*`（Diff / 引用完整性 / 灰度）
- Actuator：`aiServiceHealthIndicator`（admin-service）

### 新增 · ai-service（统一推理平台，端口 8995）

| 能力 | API |
|------|-----|
| 统一推理 | `POST /internal/ai/infer` · `GET /internal/ai/health` |
| 个性化推荐 | `POST /internal/ai/recommend/items` · `/behavior` · `/profile` |
| NPC 长期关系 | `GET /internal/ai/npc/bond` · `POST /internal/ai/npc/interact` |
| 智能客服 RAG | `POST /internal/ai/support/ask`（FAQ 缓存 → RAG → 工单） |
| 流失干预 | `POST /internal/ai/retention/evaluate` |
| 实时战术 | `POST /internal/ai/tactical/advise` |
| 内容生成校验 | `POST /internal/ai/content/generate` · `/validate` |
| 活动参数闭环 | `POST /internal/ai/ops/activity/{id}/feedback` · `GET .../optimize` |
| MLOps | `GET/POST /internal/ai/mlops/models*` · `GET /internal/ai/mlops/drift` |
| **P15 动态叙事** | `POST /internal/ai/narrative/generate` |
| **P15 常驻伙伴** | `POST /internal/ai/companion/dialogue|remember|battle-coach|passive-explore` · MsgId **2500** |
| **P15 战斗分析** | `POST /internal/ai/analyst/feedback` · 玩家侧 `POST /api/player/ai/analyst/feedback` |
| **P15 截图路点** | `POST /internal/ai/vision/waypoint`（主动触发，会话 TTL 5min） |
| **P15 动态人格** | `POST /internal/ai/persona/resolve|style` · 设置页风格滑块 |

核心类（`mmorpg-common`）：`AiInferenceGateway`、`RecommendationEngine`、`NpcLongTermMemory`、`NpcEmotionVector`、`BossStrategyTier`、`FaqRagService`、`AiContentGuard`、`LlmDailyQuota`、`RetentionInterventionEngine`、`TacticalAdvisorEngine`、`ContentQualityValidator`、`ActivityParamOptimizer`、`ModelDriftMonitor`、**`DynamicNarrativeService`**、**`CompanionBotService`**、**`CombatAnalystService`**、**`ScreenWaypointTranslator`**、**`DynamicPersonaEngine`**。

---

## 八.五、P15 生成式沉浸补齐（管道 B）

| 能力 | 说明 |
|------|------|
| World-Context Narrative | Snapshot（天气/纪元/潮汐/编年史/好感/背包）→ 意图 JSON → `AiContentGuard` + `RuleTriggerService` 防乱发任务 → 情感台词 |
| CompanionBot | Redis 语义键 `companion:memory:{playerId}`；战况指令注入 `SquadCommanderService`；好感解锁调查点/地图标记 |
| Combat Analyst | 规则层算空转/闪避浪费/反应覆盖；口语化建议；可深链 `EquipEnhanceService` |
| Screen→Waypoint | 玩家主动截图求助；视觉标签对齐区域坐标；5 分钟过期不落盘 |
| Dynamic Persona | 聚类 CASUAL/HARDCORE/SOCIAL；`{{PLAYER_PERSONA}}` 注入；风格滑块 CONCISE/DETAILED/EMOTIONAL |

---

## 九、后续替换点

1. `BehaviorAnomalyDetector` → OnnxRuntime / Python sidecar  
2. `PlayerRiskScorer` / `RuleEngineBackend` → 离线训练 XGBoost，经 `ModelVersionRegistry` + ai-service 热切换  
3. BT 可视化 Web 编辑器（当前仅 JSON 热更 API）  
4. Prometheus 正式 histogram 替换 `AiMetrics` 进程内估计  
5. ~~每玩家日限 + FAQ Redis 缓存 + `AiContentGuard` 出站审核~~（骨架已落地，Redis 分布式计数可再接）  
6. 用户点赞/点踩 → Prompt / 微调闭环  
7. 生产默认切换私有化推理端点（禁公有 API 直连含资产上下文的请求）  
8. ~~多模态截图路点（P15 骨架）~~ → 真实 ViT / 云端多模态 API；ASR / 图像反外挂仍可扩展  
9. `DynamicNarrativeService.compress` / `CombatAnalystService.summarize` → 真实 LLM JSON 输出（规则层已作回退）

---

## 十、测试

新增单测与流程测覆盖：

- DDA：`DifficultyEvaluatorTest`、`BossStrategyTierTest`、`DynamicDifficultyServiceTest`、`DynamicDifficultyFlowTest`
- 反作弊：`BehaviorAnomalyDetectorTest`、`AntiCheatAdversarialFlowTest`
- AI 队友：`AiTeammateStrategyTest`、`AiTeammateBusinessFlowTest`
- Admin：`AdminAiOpsBusinessFlowTest`、`AiDraftVersionStoreTest`
- NPC：`NpcMemoryMoodFlowTest`、`NpcLongTermMemoryTest`
- 平台：`AiInferenceGatewayTest`、`RecommendationEngineTest`、`AiPlatformFlowTest`、`AiServiceSmokeTest`
- **P15**：`AiP15CapabilitiesTest`、`AiP15BusinessFlowTest`、`AiP15EdgeCaseTest`（common）；`AiP15BusinessFlowTest`、`AiPlatformBusinessFlowTest.p15*`（ai-service）；`PlayerAiP15PlatformFlowTest`（player-service）
- 既有：`MatchAndMlPlatformFlowTest`、`LlmReliabilityAndHealthFlowTest`、`ExperimentAssignerTest`

建议命令：

```bash
mvn -pl mmorpg-common,ai-service,player-service test "-Dtest=AiP15*,AiPlatformBusinessFlowTest,AiServiceSmokeTest,PlayerAiP15*,AiPlatformFlowTest"
```
