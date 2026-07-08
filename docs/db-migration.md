# 数据库拆分迁移说明（第一阶段）

## 目标
- `player-service` 使用 `player_db`
- `battle-service` 使用 `battle_db`
- `activity-service` 使用 `activity_db`

## 已落地
- `player-service` 数据源默认库已改为 `${MYSQL_DB_PLAYER:player_db}`
- `battle-service` 数据源默认库已设为 `${MYSQL_DB_BATTLE:battle_db}`
- `activity-service` 数据源默认库已设为 `${MYSQL_DB_ACTIVITY:activity_db}`
- 初始化脚本：
  - `player-service/src/main/resources/schema/player_db.sql`
  - `battle-service/src/main/resources/schema/battle_db.sql`
  - `activity-service/src/main/resources/schema/activity_db.sql`

## 迁移步骤建议
1. 在 MySQL 创建三个 schema。
2. 分别执行三个初始化脚本（先建表骨架）。
3. 按服务把历史数据导入对应 schema。
4. 启动服务并验证：
   - player 登录/选角正常
   - battle 开始/行动/结束正常
   - activity 读取与领奖正常
5. 逐步删除跨库直连逻辑，改为 Feign 查询或 MQ 投影。

