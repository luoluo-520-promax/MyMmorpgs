/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerTimerPersistenceService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：每个在线玩家独立定时器，周期性将 entity:player 脏数据 flush 到 MySQL。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 每个在线玩家独立定时器，周期性将 entity:player 脏数据 flush 到 MySQL

import cn.itcast.demo.mymmorpg.support.SchedulerManager; // 统一调度线程池，提交 schedule 延迟任务
import jakarta.annotation.PreDestroy; // JVM 关闭前 cancel 全部玩家定时器
import org.springframework.beans.factory.annotation.Value; // game.player.persist-interval-ms 刷盘间隔
import org.springframework.stereotype.Service; // 玩家级定时刷盘 Bean，选角 startPlayerTimer、登出 stopPlayerTimer

import java.util.concurrent.ConcurrentHashMap; // playerId -> ScheduledFuture 映射
import java.util.concurrent.ScheduledFuture; // 可 cancel 的延迟/周期任务句柄

/**
 * 玩家级脏数据定时刷盘：选角后 startPlayerTimer，登出 stopPlayerTimer 并最后一次 flush。
 * 与 PlayerEntityCacheService.saveCacheAndMarkDirty 配合，降低大世界高频写库压力。
 */
@Service // 在线玩家 entity:player 脏数据按 persistIntervalMs 周期 flush 至 MySQL
public class PlayerTimerPersistenceService { // 玩家级脏数据定时刷盘：选角后 startPlayerTimer，登出 stopPlayerTimer 并最后一次 flush

    /** 当前活跃的玩家定时任务：playerId -> 下一次 tick 的 ScheduledFuture */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> playerTimers = new ConcurrentHashMap<>(); // 当前活跃的玩家定时任务：playerId -> 下一次 tick 的 ScheduledFuture

    /** 游戏服统一调度器，避免每玩家 new Thread */
    private final SchedulerManager schedulerManager; // 游戏服统一调度器，避免每玩家 new Thread

    /** 玩家实体缓存，flushDirtyNow 读缓存写 MySQL，clearDirtyFlag 清脏标记 */
    private final PlayerEntityCacheService playerEntityCacheService; // 玩家实体缓存，flushDirtyNow 读缓存写 MySQL，clearDirtyFlag 清脏标记

    /** game.player.persist-interval-ms：脏 Player 缓存周期 flush 间隔，默认 5000ms */
    private final long persistIntervalMs; // game.player.persist-interval-ms：脏 Player 缓存周期 flush 间隔，默认 5000ms

    /**
     * 构造器：调度器、缓存服务与刷盘间隔配置。
     */
    public PlayerTimerPersistenceService( // 构造器：调度器、缓存服务与刷盘间隔配置
            SchedulerManager schedulerManager, // 统一 schedule 延迟任务
            PlayerEntityCacheService playerEntityCacheService, // flushDirtyNow 写 MySQL
            @Value("${game.player.persist-interval-ms:5000}") long persistIntervalMs) { // 在线玩家 entity:player 脏数据刷盘周期（毫秒）
        this.schedulerManager = schedulerManager; // 链式 schedule 周期 tick
        this.playerEntityCacheService = playerEntityCacheService; // 读缓存写 player 表
        this.persistIntervalMs = Math.max(1000L, persistIntervalMs); // 最小 1000ms 防配置过小打爆 DB
    }

    /**
     * 玩家上线/选角后启动独立定时器：若已有旧任务先 cancel，再 schedule 首次 tick。
     *
     * @param playerId 已选角色 ID
     */
    public void startPlayerTimer(long playerId) { // 玩家上线/选角后启动独立定时器：若已有旧任务先 cancel，再 schedule 首次 tick
        if (playerId <= 0) { // playerId 非法
            return; // 不启动定时器
        }
        playerTimers.compute(playerId, (id, oldFuture) -> { // 同 playerId 仅保留一个 flush 定时任务
            if (oldFuture != null) { // 重选角或重连已有旧定时器
                oldFuture.cancel(false); // 取消旧链，不 interrupt 正在执行的 flush
            }
            return scheduleNext(id); // 安排首次延迟 tick
        }); // compute 结束：取消旧 ScheduledFuture 并 scheduleNext
    }

    /**
     * 登出时停止定时器、最后一次 flush 脏数据并清除脏标记，保证数据不丢。
     *
     * @param playerId 下线角色 ID
     */
    public void stopPlayerTimer(long playerId) { // 登出时停止定时器、最后一次 flush 脏数据并清除脏标记，保证数据不丢
        if (playerId <= 0) { // playerId 非法
            return; // 无需停止
        }
        ScheduledFuture<?> future = playerTimers.remove(playerId); // 从 Map 移除，登出后不再 schedule
        if (future != null) { // 存在活跃定时任务
            future.cancel(false); // 停止后续 tick
        }
        playerEntityCacheService.flushDirtyNow(playerId); // 登出前同步写库
        playerEntityCacheService.clearDirtyFlag(playerId); // 清除脏标记
    }

    /**
     * 安排单次延迟任务，tick 内在 finally 中链式 schedule 下一次，形成周期刷盘。
     */
    private ScheduledFuture<?> scheduleNext(long playerId) { // 安排单次延迟任务，tick 内在 finally 中链式 schedule 下一次，形成周期刷盘
        return schedulerManager.schedule(() -> onTimerTick(playerId), persistIntervalMs); // persistIntervalMs 后执行 onTimerTick
    }

    /**
     * 定时回调：尝试 flush 脏玩家数据；若玩家仍在线（playerTimers 仍有条目）则 schedule 下一轮。
     */
    private void onTimerTick(long playerId) { // 定时回调：尝试 flush 脏玩家数据；若玩家仍在线（playerTimers 仍有条目）则 schedule 下一轮
        try { // flush 失败时仍须在 finally 链式 schedule 下一轮 tick
            playerEntityCacheService.flushDirtyNow(playerId); // 脏数据写 MySQL
        } finally { // 保证在线玩家定时器链不中断
            playerTimers.computeIfPresent(playerId, (id, old) -> scheduleNext(id)); // 仍在线则链式下一轮 tick
        }
    }

    /**
     * 进程关闭时 cancel 全部玩家定时任务，避免 shutdown 后仍写库。
     */
    @PreDestroy // JVM 关闭前 cancel 全部玩家定时器，避免 shutdown 后仍写库
    void shutdown() { // 进程关闭时 cancel 全部玩家定时任务，避免 shutdown 后仍写库
        for (ScheduledFuture<?> future : playerTimers.values()) { // 遍历全部玩家定时器
            if (future != null) { // 跳过 null 条目
                future.cancel(false); // 停止 tick
            }
        }
        playerTimers.clear(); // 清空 Map
    }
}
