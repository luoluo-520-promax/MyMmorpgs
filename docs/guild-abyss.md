# 公会 + 深渊（本轮落地）

对应产品缺口：差距一（公会）与差距四（深渊）。其余缺口仅预留表/字段，玩法未实现。

## 公会 guild-service（端口 8998）

| 能力 | 说明 |
|------|------|
| 库表 | `guild_db`：`guild` / `guild_member` / `guild_tech` |
| 创建 | 扣 摩拉×30000(`10001`) + 原石×300(`10002`)，不足 `ERR_INSUFFICIENT` |
| 人数 | 等级 1/2/3 → 20/30/40 |
| 科技 | ATTACK/HP/STAMINA 各 3 级；攻/血 +2%/级，体力 +5/级 |
| 远征 | 会长开启；Boss HP=`1e6*(1+0.2*lv)`；ZSET `guild:boss:damage:{guildId}` |
| 结算 | `POST /internal/guild/expedition/settle`；前 10/30/50% → 公会币 `90001`×200/100/50 |
| 定时 | 每日 4:00 会长 7 日未登录转让；每周一 5:00 远征重置 |
| 聊天 | `POST /internal/chat/guild/created` + `/member/sync` 维护 `chat:guild:*` roster |

## 深渊（并入 battle-service:8991）

包：`cn.itcast.demo.mymmorpg.abyss`（与仓库包名对齐，非独立进程）。

| 能力 | 说明 |
|------|------|
| 结构 | 3 层 × 4 间；配置 `config/abyss/abyss_floor_config.json` |
| 会话 | Redis `abyss:session:{playerId}`（血量/能量/CD 连打保留） |
| 星级 | Redis `abyss:stars:{playerId}`；剩时 ≥60/30/通关 → 3/2/1 星 |
| 奖励 | 累计 9/12/15 星系统邮件（原石 / 原石+圣遗物碎片） |
| 重置 | Cron `0 0 5 1,16 * ?` |
| API | `/internal/battle/abyss/start`、`/chamber/finish`、`/progress`、`/config/reload` |

## 预留（本轮不实现玩法）

- `player_characters`（命座等级）
- `player_bag_item.suit_id`
- `fishing_spot` / `cooking_recipe`
- `world_incident_template`
- 名片 Redis Hash 字段定义见产品定稿（未落代码）
