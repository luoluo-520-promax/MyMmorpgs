# 服务边界与契约（第一批拆分）

本文用于把当前单体代码按领域拆分为 Spring Cloud Alibaba 微服务的**最小可落地集合**，并列出第一批需要的同步 API（Feign）与异步事件（RocketMQ）。

## 目标服务（第一批）

### player-service（玩家/会话/长连接入口）
- **职责**
  - 账号与角色：登录、玩家基础信息、玩家状态（等级、经验、背包/技能的入口聚合）
  - 统一鉴权与会话：token、在线管理
  - **长连接入口（Netty/WebSocket）**：接入客户端消息、路由到内部服务、回写响应
- **拥有数据**
  - `Account`、`Player` 以及与玩家会话强相关的数据（在线、token、最近登录等）
- **对外（Gateway）入口**
  - HTTP：登录、管理端（如有）
  - WebSocket：可选
  - TCP/Netty：游戏二进制协议（现有）

### battle-service（战斗）
- **职责**
  - 战斗开始/行动/结算等核心流程
  - 战斗事件发布（领域事件）
- **拥有数据**
  - 战斗相关运行时与配置（例如怪物/关卡配置的查询方式需明确：独立配置库或配置中心下发）
- **对外入口**
  - 仅内部调用（Feign / 事件驱动），不直接暴露给客户端

### activity-service（活动/进度）
- **职责**
  - 活动配置、活动进度、领奖逻辑
  - 订阅战斗/玩家等领域事件更新进度
- **拥有数据**
  - `Activity`、玩家活动进度等

## 同步接口（Feign）清单（第一批）

### player-service -> battle-service
- **BattleCommand API（命令型）**
  - `POST /internal/battle/start`
  - `POST /internal/battle/action`
  - `POST /internal/battle/end`
- **说明**
  - player-service 负责接入与回包；battle-service 只做战斗计算与状态推进并返回结果数据。

### battle-service -> player-service
- **PlayerQuery API（查询型）**
  - `GET /internal/players/{playerId}/profile`：战斗所需玩家基础属性快照（等级/攻击/防御/血量等按实际补齐）
  - `GET /internal/players/{playerId}/inventory/summary`（可选）：战斗掉落结算校验所需

### activity-service <- (player/battle)-service
- 初期尽量不要同步依赖，优先用事件订阅（见下文）。

## 异步事件（RocketMQ）清单（第一批）

### battle-service 领域事件
- `BattleStarted`
- `BattleEnded`
- 建议事件体：JSON 或 Protobuf（带 `eventVersion`），便于演进兼容。

### activity-service 订阅
- 订阅 `BattleEnded` 更新活动进度、发放奖励资格等（最终一致）。

## 数据拆分（第一批）
- **player-db**：账号/玩家表、会话/权限（如有）
- **battle-db**：战斗运行时（如落库）、战斗记录（可选）
- **activity-db**：活动与玩家活动进度

## 增量迁移原则
- 先让 `player-service` 成为唯一对外入口（含长连接），其它服务只做领域能力。
- 先拆 battle（调用链清晰、天然事件驱动），再拆 activity（主要消费事件）。
- 跨服务一致性优先用“事件驱动 + 幂等处理 + 最终一致”，必要时再引入分布式事务方案。

