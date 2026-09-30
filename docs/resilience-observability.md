# 可观测与容错基线

## 已落地
- 网关与服务统一 `X-Trace-Id` 透传：
  - Gateway: `TraceIdGlobalFilter`
  - 服务侧: `TraceIdFilter`（Servlet）
- Gateway 已配置基础路由：
  - `/player/**` -> `player-service`
  - `/battle/**` -> `battle-service`
  - `/activity/**` -> `activity-service`
- Gateway 区域就近：`RegionAwareRoutingFilter`（`X-Region` / `X-Edge-Pop`）
- `player-service` 已配置 OpenFeign 超时与 circuit-breaker 开关。
- Feign `FallbackFactory`：`SceneCommandClient` / `HallCommandClient` / `BattleCommandClient`
- 各服务 Actuator 已暴露 `health/info/metrics/prometheus`（网关与服务）。
- P0 告警骨架：`deploy/observability/prometheus.yml` + `alerts.yml`（验签失败、履约停滞、实例宕机、堆内存）。

## 建议下一步
- 接入统一日志采集（ELK/Loki）并以 `traceId` 做检索键。
- 为其余 Feign 客户端补全 fallback；战斗链路优先本地/粘滞以降低跨区 RTT。
- 基于 Gateway 增加登录/下游路由的限流规则（Sentinel 或 Redis 令牌桶）。
- 将 Alertmanager 接到企业微信/钉钉；多活容灾演练（区域故障切换）。
- Outbox 已暴露 `mmorpg.mq.outbox_failed` / `mmorpg.mq.outbox_new` Gauge，告警见 `deploy/observability/alerts.yml`。
- **AI 专属指标**（详见 `docs/ai-enhancement.md`）：建议采纳率、对话中断率（超时→Rule-Coach）、管道 B 首包 P99、Token 消耗速率；管道 A 的 BT Tick P99 须 &lt; 50ms；计划中点赞/点踩反馈。

## TraceId + Micrometer Tracing / Zipkin / OpenTelemetry

### 现状
- HTTP：`TraceIdFilter` 读写 `X-Trace-Id`，写入 SLF4J MDC 键 `traceId`。
- 跨线程 / MQ / 异步：使用 `cn.itcast.demo.mymmorpg.observability.TraceContext` 的 `getTraceId` / `setTraceId` / `clear` / `ensureTraceId`，与 Filter 共用同一 MDC 键，避免线程池串线。
- 日志脱敏与采样：`LogDesensitizer`、`LogSamplingFilter`（INFO 可按比例采样，WARN/ERROR 始终保留）。
- MQ：`DeadLetterQueueService`（内存 + 可选 Redis）、`MqLagMonitor`（lagMs / backlog，阈值 `game.mq.lag.alert-threshold-ms`）。

### 接入 Micrometer Tracing（推荐 Spring Boot 3）
1. 依赖（示例）：
   - `micrometer-tracing-bridge-otel` 或 `micrometer-tracing-bridge-brave`
   - 导出：`opentelemetry-exporter-otlp` 或 `zipkin-reporter-brave`
2. 配置示例：
   ```yaml
   management:
     tracing:
       sampling:
         probability: 1.0   # 生产可降到 0.1
     zipkin:
       tracing:
         endpoint: http://zipkin:9411/api/v2/spans
   # 或 OTLP：
   # management.otlp.tracing.endpoint: http://otel-collector:4318/v1/traces
   ```
3. 与业务 `traceId` 对齐：Micrometer / OTel 默认用 `traceId` W3C / B3。可在 Feign / RestTemplate 拦截器中把 `TraceContext.getTraceId()` 写入 `X-Trace-Id` 与 `traceparent`，使 ELK 检索键与 Zipkin UI 一致。
4. Resilience4j 熔断示例见 `deploy/observability/circuitbreaker-example.yml`（scene/battle Feign：`failureRateThreshold`、`waitDurationInOpenState`、半开 `permittedNumberOfCallsInHalfOpenState`）。

