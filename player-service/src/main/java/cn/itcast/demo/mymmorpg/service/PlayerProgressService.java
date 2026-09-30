/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerProgressService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：统一处理经验增加、等级曲线计算与升级事件发布。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 统一处理经验增加、等级曲线计算与升级事件发布

import cn.itcast.demo.mymmorpg.entity.Player; // JPA 玩家实体，含 exp、level 字段
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort; // 供 battle-service、activity-service 等模块调用加经验的端口
import cn.itcast.demo.mymmorpg.event.PlayerLevelUpEvent; // 升级事件，订阅方可解锁功能、推送客户端 Notify
import cn.itcast.demo.mymmorpg.support.RankingScoreStore;
import jforgame.commons.eventbus.EventBus; // 进程内事件总线，同步发布升级事件
import org.springframework.stereotype.Service; // 经验与升级 Bean，battle/activity 经 PlayerProgressPort 调用 addExp
import org.springframework.transaction.annotation.Transactional; // 加经验与写缓存同一事务边界（脏标记落库由定时器异步刷）

/**
 * 玩家进度服务：战斗/任务奖励经验统一入口，升级时发布 PlayerLevelUpEvent 驱动功能解锁与推送。
 */
@Service // 统一加经验、1000 经验/级曲线计算，升级时发布 PlayerLevelUpEvent
public class PlayerProgressService implements PlayerProgressPort { // 玩家进度服务：战斗/任务奖励经验统一入口，升级时发布 PlayerLevelUpEvent 驱动功能解锁与推送

    /** 玩家实体缓存，saveCacheAndMarkDirty 更新内存缓存并打脏标记，定时器异步刷 MySQL */
    private final PlayerEntityCacheService playerEntityCacheService; // 玩家实体缓存，saveCacheAndMarkDirty 更新内存缓存并打脏标记，定时器异步刷 MySQL

    /** 事件总线，升级时通知 FunctionService 等功能模块 */
    private final EventBus eventBus; // 事件总线，升级时通知 FunctionService 等功能模块

    private final RankingScoreStore rankingScoreStore;

    /**
     * 构造器：缓存服务、事件总线与排行榜写回。
     */
    public PlayerProgressService(PlayerEntityCacheService playerEntityCacheService,
                                 EventBus eventBus,
                                 RankingScoreStore rankingScoreStore) {
        this.playerEntityCacheService = playerEntityCacheService; // entity:player 缓存与脏标记
        this.eventBus = eventBus; // PlayerLevelUpEvent 驱动功能解锁
        this.rankingScoreStore = rankingScoreStore;
    }

    @Override // PlayerProgressPort.addExp：战斗/任务奖励经验统一入口
    @Transactional // 事务包裹缓存更新，脏数据由 PlayerTimerPersistenceService 异步 flush
    public Player addExp(Player player, int expGained) { // 构造器：缓存服务与事件总线
        if (player == null || player.getId() == null || expGained <= 0) { // 无效玩家或经验增量
            return player; // 原样传回，不写缓存
        }
        long oldExp = player.getExp() == null ? 0L : player.getExp(); // 加经验前累计值
        int oldLevel = player.getLevel() == null ? 1 : player.getLevel(); // 加经验前等级

        long newExp = oldExp + expGained; // 累加战斗/任务奖励经验
        player.setExp(newExp); // 写回 Player.exp 字段

        int newLevel = calcLevel(newExp); // 按 1000 经验/级曲线反算等级
        if (newLevel != oldLevel) { // 等级发生变化
            player.setLevel(newLevel); // 更新 Player.level
            if (newLevel > oldLevel) {
                int pts = player.getTalentPoints() == null ? 0 : player.getTalentPoints();
                player.setTalentPoints(pts + (newLevel - oldLevel)); // 每升 1 级奖励 1 天赋点
            }
        }
        player.recalcPowerScore();
        Player saved = playerEntityCacheService.saveCacheAndMarkDirty(player); // 更新 entity:player 缓存并打脏
        rankingScoreStore.updatePlayer(
                saved.getId(),
                saved.getLevel() == null ? 1 : saved.getLevel(),
                saved.getPowerScore() == null ? 0 : saved.getPowerScore());

        if (newLevel > oldLevel) { // 升级发生
            eventBus.publish(new PlayerLevelUpEvent(saved, oldLevel, newLevel)); // FunctionFacade 触发等级型功能解锁
        }
        return saved; // 含最新 exp/level 的 Player 实体
    }

    @Override
    @Transactional
    public Player spendGold(Player player, long amount) {
        if (player == null || player.getId() == null || amount <= 0) {
            return player;
        }
        long gold = player.getGold() == null ? 0L : player.getGold();
        if (gold < amount) {
            return null;
        }
        player.setGold(gold - amount);
        return playerEntityCacheService.saveCacheAndMarkDirty(player);
    }

    @Override
    @Transactional
    public Player addGold(Player player, long amount) {
        if (player == null || player.getId() == null || amount <= 0) {
            return player;
        }
        long gold = player.getGold() == null ? 0L : player.getGold();
        player.setGold(gold + amount);
        return playerEntityCacheService.saveCacheAndMarkDirty(player);
    }

    /**
     * 简单等级曲线：每 1000 经验升 1 级，1 级起算（可按需替换为表驱动 exp_level 配置）。
     *
     * @param exp 累计经验值
     * @return 对应等级，上限 Integer.MAX_VALUE
     */
    static int calcLevel(long exp) { // 简单等级曲线：每 1000 经验升 1 级，1 级起算（可按需替换为表驱动 exp_level 配置）
        long e = Math.max(0, exp); // 负经验钳制为 0
        long lv = e / 1000L + 1L; // 每 1000 经验升 1 级，1 级起算
        if (lv > Integer.MAX_VALUE) { // 溢出保护
            return Integer.MAX_VALUE; // 等级上限
        }
        return (int) lv; // 当前等级
    }
}
