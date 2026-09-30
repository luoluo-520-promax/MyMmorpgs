# 故障排查手册

## 网络拓扑（逻辑）

```text
Client (TCP/KCP/WS)
    │
    ▼
mmorpg-gateway (8443) ── RegionAware + FunctionNumber + SeamlessZone
    │
    ├─► player-service (8989) ── 会话 / 路由 / 嵌入域
    ├─► scene-service (8981) ── AOI / WorldState / Portal
    ├─► battle-service (8991)
    ├─► hall-service (8986) ── 好友/邮件/队伍/助战/家园/CoopRoom
    ├─► social-service (8996) ── 关系图谱/亲密度/黑名单/社交成就
    ├─► ai-service (8995) ── AI 推理网关（推荐/客服/内容校验/MLOps）
    ├─► chat-service (8982) ── 聊天/自定义频道/反骚扰
    ├─► shop-service (8990) ── 订单/账本/对账/战令
    ├─► activity / quest / bag / skill / gacha / update / matchmaking / admin
    │
    ├─ MySQL（player/battle/activity/update…）
    ├─ Redis（在线/匹配/票据/社交快照）
    └─ MQ Outbox / Inbox（可选 RocketMQ）
```

## 常见故障

### 1. Redis 连接失败

**现象**：启动报 `Unable to connect to Redis`，或好友/匹配/票据异常。

**步骤**：
1. `docker compose ps` 确认 redis 健康。
2. 检查 `spring.data.redis.host/port` 与 `REDIS_HOST` 环境变量。
3. 开发可用嵌入式 Redis；生产禁止回退空实现。
4. 看 Actuator `/actuator/health` 中 redis 组件。

### 2. MQ 积压 / Outbox 失败

**现象**：Prometheus `mmorpg.mq.outbox_failed` / `outbox_new` 升高；支付后未发奖。

**步骤**：
1. 查 `mq_outbox` 表 `NEW/FAILED` 行。
2. 使用 `DeadLetterQueueService` / 运维接口查看死信并人工 `retry`。
3. 对照 `MqLagMonitor.lagMs(topic)` 是否超过告警阈值。
4. 确认 RocketMQ/Kafka 开关：`game.mq.enabled` / `game.tlog.kafka.enabled`。

### 3. 内部 API 401（HMAC）

**现象**：Feign 调 `/internal/**` 返回 401。

**步骤**：
1. 各服务 `INTERNAL_API_SECRET` 必须一致。
2. 生产 `ProductionInternalApiSecretValidator` 会拒绝弱密钥。
3. 时钟偏差过大导致签名过期时，对齐 NTP。

### 4. 支付对账差异

**现象**：`reconcile` 返回 mismatch / retry candidates。

**步骤**：
1. 用 `CsvChannelBillProvider` / `JsonChannelBillProvider` 导入渠道账单样例核对格式。
2. 开启 `shop.reconcile.auto-enabled` 观察定时任务日志。
3. 差异单优先人工确认再触发补单；退款走 `/internal/shop/orders/{id}/refund`。

### 5. 跨服传送黑屏 / 票据失效

**现象**：Portal 切换失败 `lease_expired`。

**步骤**：
1. 确认 `PortalPreloadService` 预加载半径与 TTL（默认 ≥3s）。
2. 检查 `MigrationTicketService` Redis 键 `center:migration:ticket:*`。
3. 客户端应消费 `assets` 后台下载字段，避免进门才拉包。

### 6. 熔断导致 scene/battle 不可用

**现象**：Feign Fallback 返回降级响应。

**步骤**：
1. 对照 `deploy/observability/circuitbreaker-example.yml` 阈值。
2. 查下游延迟与错误率；半开探测恢复后再放量。
3. 结合 TraceId（`X-Trace-Id` / MDC）在 ELK 拉端到端链路。

## OpenAPI / 协议文档

- 内部 HTTP：可在各服务引入 `springdoc-openapi` 后访问 `/swagger-ui.html`（见 `docs/openapi.md`）。
- 游戏协议：对 `mmorpg-protocol` 执行 `protoc --doc_out` 生成 HTML（示例命令见同文档）。
- Postman：`docs/postman/MyMmorpg-internal.postman_collection.json`
