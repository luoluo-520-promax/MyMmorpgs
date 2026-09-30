# OpenAPI 与协议文档

## HTTP 内部接口（SpringDoc）

在需要暴露文档的服务 `pom.xml` 增加：

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.5.0</version>
</dependency>
```

配置：

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

建议仅对 **admin-service / 内网网关** 暴露，生产用 IP 白名单或关闭。

已整理的主要路径见 Postman 集合：`docs/postman/MyMmorpg-internal.postman_collection.json`。

## 游戏协议 HTML（protoc-gen-doc）

```bash
# 需安装 protoc 与 protoc-gen-doc
protoc --doc_out=html,index.html:docs/protocol \
  --proto_path=mmorpg-protocol/src/main/proto \
  mmorpg-protocol/src/main/proto/**/*.proto
```

输出目录：`docs/protocol/index.html`。

## 关键入口速查

| 能力 | 路径 |
|------|------|
| 热更 | `POST /admin/ops/reload` |
| 配置发布/回滚 | `/admin/ops/config/*` |
| 运营控制台 | `GET /ops-console.html`（admin-service static） |
| 助战 | `/internal/hall/assist/*` |
| 队伍 | `/internal/hall/party/*` |
| 家园 | `/internal/hall/home/*` |
| 联机房间 | `/internal/hall/coop/*` |
| 社交关系图谱/成就 | `/internal/social/*` |
| 自定义聊天频道 | `/internal/chat/channel/*` |
| 聊天拉黑/骚扰分 | `/internal/chat/block`、`/internal/chat/harassment/score` |
| 战令订阅 | `/internal/shop/pass/auto-renew` 等 |
| 聊天举报 | `/internal/chat/report` |
| NPC 对话 | `POST /internal/npc/dialogue` |
| DLQ / 滞后 | 见 `DeadLetterQueueService` / `MqLagMonitor` |
