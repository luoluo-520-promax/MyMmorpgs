# MyMmorpg

Java 17 / Spring Boot 3 的 MMORPG 服务端示例：支持**单体**（默认仅 `player-service`）与**微服务拆分**两种部署模式。

## 快速开始（单体）

```bash
# 1. 启动依赖
docker compose up -d mysql redis

# 2. 复制环境变量（开发默认即可）
cp .env.example .env

# 3. 启动玩家服（嵌入场景/大厅/任务/匹配/背包等）
mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev
```

客户端默认连 `player-service`：HTTP `8989`，Netty 游戏端口见 `config/`。

## 文档

| 文档 | 内容 |
|------|------|
| [DEPLOYMENT.md](DEPLOYMENT.md) | 部署模式、环境变量矩阵、已实现 / 演示 / 计划中 |
| [docs/ai-enhancement.md](docs/ai-enhancement.md) | AI 双管道隔离、成本限流、RAG、内容风控、Admin 闭环与指标 |
| [docs/troubleshooting.md](docs/troubleshooting.md) | 拓扑图与常见故障排障 |
| [docs/openapi.md](docs/openapi.md) | OpenAPI / 协议文档 / Postman |
| [perf/README.md](perf/README.md) | JMeter / Gatling 压测脚本 |
| [.env.example](.env.example) | 全量环境变量样例 |
| `player-service/.../schema/player_db.sql` | 玩家库（含好友/邮件/任务进度表） |

## 常用命令

```bash
mvn -B test                          # 单元测试
mvn -B test -Pintegration-tests      # 集成测试
mvn -B verify -Pquality-gate         # JaCoCo + SpotBugs
docker compose up -d                 # MySQL + Redis + Nacos
```

## 模块一览

`mmorpg-common` · `player-service` · `ai-service`（8995） · `scene/chat/bag/skill/hall/quest/matchmaking/gacha/battle/activity/update/admin/shop/guild/social` · `mmorpg-gateway` · `mmorpg-cli`

## 与 MyLunarCore 对齐要点

- **运维面优先外拆**：Admin 配置导入 + `POST /admin/ops/reload` 分阶段热更与发布审计
- **AI 双管道**：确定性战斗 AI（Boss BT / AI 队友，Tick &lt;50ms，禁 LLM）与生成式交互 AI（顾问 / 智能 NPC，异步+超时回退 `rule-coach`）；详见 [docs/ai-enhancement.md](docs/ai-enhancement.md)
- **二游核心玩法**：元素反应（Aura→Trigger→Reaction）；体力 Resin；装备随机词条；战令/月卡/首充双倍；七日签到
- **社交形态**：联机房间 CoopRoom（≤4 人共斗 + 观战/表情互动）优先于千人同屏 AOI；家园小游戏/评分商店；统一社交关系服务（亲密度/黑名单/成就）；跨服好友 Redis；`SOCIAL_EVENTS` 驱动成就与活动
- **游戏实时面**：战斗 / 场景走 Netty TCP + 可选 UDP/KCP；Center + GlobalUID（雪花）；大厅好友在线；匹配互补评分；皮肤 `12xx`；挑战 `13xx`；**独立抽卡服** `14xx`；肉鸽 `15xx`
- **数据与观测**：玩家/背包冷热分离（Redis 热 + 定时落 MySQL）；场景 SnapshotBuffer 延迟补偿；TLog（Kafka 可选）
- 详见 [DEPLOYMENT.md](DEPLOYMENT.md)
