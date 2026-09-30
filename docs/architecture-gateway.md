# Gateway-only 与 common 拆分路线

## 目标

1. **player-service** 作为网关进程：仅依赖 `mmorpg-common` + Feign，不再 Maven 编译依赖各域 JAR。
2. **mmorpg-common** 拆为 `protocol` / `domain-api` / `infra`（渐进）。

## 当前进度

| 项 | 状态 |
|----|------|
| `mmorpg-protocol` 接管全部 `*.proto` + 生成代码 | **已完成** |
| MessageId / GamePackets / RetCode / ProtocolMessage 等 | **已完成**（在 `mmorpg-protocol`；common 残留已删除） |
| `mmorpg-domain-api`（`BagCommandPort` / `BagItemGrantPort`） | **已完成**（common 依赖并传递） |
| player `embed-admin` profile（默认开） | **已完成**；`-P'!embed-admin'` 可去掉 Admin JAR |
| player `embed-bag` profile（默认开） | **已完成**；`-P'!embed-bag'` 可去掉 Bag JAR |
| Bag/Skill/Activity 预加载与 BagGateway 用 `ObjectProvider` | **已完成** |
| Gacha / BagGateway / 预加载依赖 Port 而非 `BagService` 类型 | **已完成** |
| PlayerAi 不再依赖 admin 的 `BattleStatsClient` | **已完成**（改用 `PlayerBattleStatsClient`） |
| 摘掉 battle/skill/activity/… 域依赖 | 未完成（仍有硬类型引用） |

## 验证命令

```bash
# 默认单体（含 admin + bag）
mvn -pl player-service -am package -DskipTests

# 协议 + domain-api + common
mvn -pl mmorpg-protocol,mmorpg-domain-api,mmorpg-common -am package -DskipTests

# 不嵌入 admin / bag（验证网关编译可无对应域 JAR）
mvn -pl player-service -am package -DskipTests "-P!embed-admin"
mvn -pl player-service -am package -DskipTests "-P!embed-bag"
mvn -pl player-service -am package -DskipTests "-P!embed-admin,!embed-bag"
```

## gateway-only 剩余阻塞

仍需消除对域实现类的 **编译期 import**，例如：

- `BattleCommandGateway` → 本地 `BattleService`
- `RogueFacade` → 嵌入的 `RogueService`
- 预加载仍对 `SkillService` / `ActivityService` 硬类型

建议步骤：

1. 为 battle/skill/activity 等补齐 `*CommandPort` 到 `mmorpg-domain-api`。
2. 所有业务路径经 Gateway + Feign（`GAME_*_REMOTE_ENABLED=true`）。
3. 从 `player-service/pom.xml` 删除域模块依赖；CI 增加无域 JAR 编译任务。

## common 拆分边界

| 模块 | 内容 | 进度 |
|------|------|------|
| mmorpg-protocol | `*.proto` + MessageId/GamePackets/RetCode 等 | 已建 |
| mmorpg-domain-api | 跨服务 Port（已含 Bag 命令/发货） | 已建（可继续迁入其余 port） |
| mmorpg-infra | `net/`、`rpc/`、`support/`、`config/`、`web/` | 未建 |
| mmorpg-common | Spring 适配、远程 Port、过渡聚合 | 仍为聚合层 |

下一阶段：为 battle/skill/activity 抽取 CommandPort；再建 `mmorpg-infra`。
