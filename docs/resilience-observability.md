# 可观测与容错基线

## 已落地
- 网关与服务统一 `X-Trace-Id` 透传：
  - Gateway: `TraceIdGlobalFilter`
  - 服务侧: `TraceIdFilter`（Servlet）
- Gateway 已配置基础路由：
  - `/player/**` -> `player-service`
  - `/battle/**` -> `battle-service`
  - `/activity/**` -> `activity-service`
- `player-service` 已配置 OpenFeign 超时与 circuit-breaker 开关。
- 各服务 Actuator 已暴露 `health/info/metrics/promptheus`（网关与服务）。

## 建议下一步
- 接入统一日志采集（ELK/Loki）并以 `traceId` 做检索键。
- 为 Feign 调用补全 fallback 策略（按接口粒度）。
- 基于 Gateway 增加登录/下游路由的限流规则（Sentinel 或 Redis 令牌桶）。

