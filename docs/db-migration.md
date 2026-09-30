# 数据库拆分迁移说明（第一阶段）

## 目标
- `player-service` 使用 `player_db`
- `battle-service` 使用 `battle_db`
- `activity-service` 使用 `activity_db`
- `update-service` 使用 `update_db`

## 唯一权威 DDL

| 库 | 权威脚本 |
|----|----------|
| `player_db` | `player-service/src/main/resources/schema/player_db.sql` |
| `battle_db` | `battle-service/src/main/resources/schema/battle_db.sql`（Compose 包装：`docker/mysql/02-battle_db.sql`） |
| `activity_db` | `activity-service/src/main/resources/schema/activity_db.sql`（与 JPA `Activity` 实体对齐：`type/opened/data/create_time/update_time`） |
| `update_db` | `update-service/src/main/resources/schema/update_db.sql`（Compose 包装：`docker/mysql/04-update_db.sql`） |

活动进度以 **Redis JSON** 为主，**不要**再创建过时的 `player_activity_progress` 表。  
`player-service/.../schema/activity_system.sql` 与独立库 `activity_db.sql` 的 `activity` 表结构应保持一致。

## 已落地
- 各服务数据源默认库：`${MYSQL_DB_PLAYER/BATTLE/ACTIVITY/UPDATE}`
- `docker compose` 首次启动执行：
  1. `docker/mysql/00-create-databases.sql` 创建四库
  2. `player_db.sql` / `02-battle_db.sql` / `03-activity_db.sql` / `04-update_db.sql`
- **Flyway（P0）**：`player-service` / `activity-service` / `shop-service` 启用 `classpath:db/migration`，`baseline-on-migrate=true`；新增表以 `V1__p0_*.sql` 为准，权威 DDL 同步维护在各服务 `schema/*.sql`。
- **经济硬化表（player_db）**：`wallet_ledger` / `item_ledger` / `grant_idempotency` / `shop_order` / `mq_outbox` / `mq_inbox`
- **活动库**：`mq_inbox`（消费幂等）

## 两套初始化路径

### 单体模式（默认）
只需 `player_db`（域表集中在 player 库）。Compose 默认 `MYSQL_DATABASE=player_db` 已够用。

```bash
docker compose up -d mysql redis
# 已有数据卷时不会重跑 init；需重建：docker compose down -v && docker compose up -d mysql
```

### 微服务拆分模式
必须四库齐全；Compose 已自动创建并挂载 schema。手工初始化：

```sql
CREATE DATABASE IF NOT EXISTS player_db DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS battle_db DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS activity_db DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS update_db DEFAULT CHARSET utf8mb4;
```

然后分别对对应库执行权威脚本。

## 迁移步骤建议
1. 在 MySQL 创建四个 schema（或使用 Compose）。
2. 分别执行权威初始化脚本。
3. 按服务把历史数据导入对应 schema。
4. 启动服务并验证：登录/选角、战斗、活动领奖、更新清单。
5. 逐步删除跨库直连，改为 Feign / MQ。
