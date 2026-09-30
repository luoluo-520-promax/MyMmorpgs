# MyMmorpg 压测脚本说明

覆盖登录、移动、战斗、支付等高频路径，可将吞吐量纳入 CI 门禁参考。

## JMeter

文件：`perf/jmeter/mmorpg-smoke.jmx`

```bash
jmeter -n -t perf/jmeter/mmorpg-smoke.jmx -l perf/out/smoke.jtl -e -o perf/out/html
```

默认目标：`http://localhost:8989`（player-service）。可用属性覆盖：

- `-Jhost=127.0.0.1 -Jport=8989 -Jthreads=50 -Jduration=60`

## Gatling（Scala DSL 提纲）

见 `perf/gatling/MmorpgSmokeSimulation.scala`。本地需安装 Gatling 或使用 Maven 插件。

## 建议门禁（示例）

| 场景 | 目标 TPS | p95 |
|------|----------|-----|
| 健康检查 / 登录票据 | ≥ 500 | < 50ms |
| 场景移动上报 | ≥ 200 | < 80ms |
| 战斗结算 | ≥ 100 | < 150ms |
| 支付验单（MOCK） | ≥ 50 | < 200ms |

CI 可先跑 smoke（短时、低并发），夜间再跑 soak。
