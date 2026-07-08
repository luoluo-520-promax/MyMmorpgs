/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerDataLoadStateService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：内存态跟踪背包/技能/活动三类数据的异步预加载状态与去重。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 内存态跟踪背包/技能/活动三类数据的异步预加载状态与去重

import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort; // 定义 DataType 枚举与 isReady 查询契约
import org.springframework.stereotype.Service; // 内存态加载门控 Bean，PreloadService 与 BagService/SkillService 共享

import java.util.Map; // playerId -> State 映射
import java.util.concurrent.ConcurrentHashMap; // 多线程选角/预加载并发安全

/**
 * 玩家异步数据加载状态（进程内存）：配合 PlayerDataAsyncPreloadService 实现选角后门控与去重。
 * BagService/SkillService 在 preload 未完成时可返回 loading=true，避免客户端重复打 DB。
 */
@Service // 实现 PlayerDataLoadPort，跟踪 BAG/SKILL/ACTIVITY 的 ready/loading/dedupe 状态
public class PlayerDataLoadStateService implements PlayerDataLoadPort { // 玩家异步数据加载状态（进程内存）：配合 PlayerDataAsyncPreloadService 实现选角后门控与去重

    /** 背包预加载状态：playerId -> {ready, loading, lastStartMs} */
    private final Map<Long, State> bagStates = new ConcurrentHashMap<>(); // 背包预加载状态：playerId -> {ready, loading, lastStartMs}

    /** 技能列表预加载状态，键空间与背包独立 */
    private final Map<Long, State> skillStates = new ConcurrentHashMap<>(); // 技能列表预加载状态，键空间与背包独立

    /** 活动列表预加载状态，键空间与背包/技能独立 */
    private final Map<Long, State> activityStates = new ConcurrentHashMap<>(); // 活动列表预加载状态，键空间与背包/技能独立

    /**
     * 选角成功或重选角时重置三类状态，允许重新触发后台预加载。
     *
     * @param playerId 已选角色 ID
     */
    public void resetForPlayer(long playerId) { // 选角成功或重选角时重置三类状态，允许重新触发后台预加载
        if (playerId <= 0) { // playerId 非法
            return; // 不重置状态
        }
        bagStates.put(playerId, State.newReset()); // 背包 ready/loading 归零
        skillStates.put(playerId, State.newReset()); // 技能 ready/loading 归零
        activityStates.put(playerId, State.newReset()); // 活动 ready/loading 归零
    }

    @Override // PlayerDataLoadPort.isReady：BagService/SkillService 查询预加载是否已完成
    public boolean isReady(long playerId, DataType type) { // 选角成功或重选角时重置三类状态，允许重新触发后台预加载
        State state = states(type).get(playerId); // 读该类型预加载状态快照
        return state != null && state.ready; // ready=true 时业务可返回完整数据而非 loading
    }

    /**
     * CAS 式尝试启动一次预加载：已完成、进行中、或 dedupe 窗口内重复触发时返回 false。
     *
     * @param playerId        角色 ID
     * @param type              BAG / SKILL / ACTIVITY
     * @param dedupeWindowMs    去重窗口毫秒数，防止客户端连点选角重复提交异步任务
     * @return true 表示 caller 应提交 CompletableFuture.runAsync 加载任务
     */
    public boolean tryStart(long playerId, DataType type, long dedupeWindowMs) { // CAS 式尝试启动一次预加载：已完成、进行中、或 dedupe 窗口内重复触发时返回 false
        if (playerId <= 0) { // playerId 非法
            return false; // 不启动预加载
        }
        final boolean[] started = new boolean[]{false}; // compute lambda 向外传出是否真正启动
        long now = System.currentTimeMillis(); // 本次 tryStart 时间戳
        states(type).compute(playerId, (k, old) -> { // CAS 式尝试启动一次预加载：已完成、进行中、或 dedupe 窗口内重复触发时返回 false
            State state = old == null ? State.newReset() : old; // 无记录则新建初始状态
            if (state.ready || state.loading || now - state.lastStartMs < dedupeWindowMs) { // 已就绪/进行中/dedupe 窗口内
                return state; // 拒绝重复启动
            }
            state.loading = true; // 标记异步任务进行中
            state.lastStartMs = now; // 记录本次 tryStart 时间
            started[0] = true; // 通知外层提交 runAsync
            return state; // 写回 Map
        }); // lambda/匿名比较器结束，供排序或 stream 使用
        return started[0]; // true 时 PlayerDataAsyncPreloadService 提交后台加载
    }

    /**
     * 预加载成功：结束 loading，标记 ready=true，后续 isReady 返回 true。
     */
    public void markReady(long playerId, DataType type) { // 预加载成功：结束 loading，标记 ready=true，后续 isReady 返回 true
        update(playerId, type, true); // loading=false, ready=true
    }

    /**
     * 预加载失败：结束 loading，ready 保持 false，客户端同步请求仍可走 loadXxxNow 兜底。
     */
    public void markFailed(long playerId, DataType type) { // 预加载失败：结束 loading，ready 保持 false，客户端同步请求仍可走 loadXxxNow 兜底
        update(playerId, type, false); // loading=false, ready=false
    }

    /**
     * 原子更新 loading 结束与 ready 标志。
     */
    private void update(long playerId, DataType type, boolean ready) { // 原子更新 loading 结束与 ready 标志
        if (playerId <= 0) { // playerId 非法
            return; // 不更新状态
        }
        states(type).compute(playerId, (k, old) -> { // 原子更新 loading 结束与 ready 标志
            State state = old == null ? State.newReset() : old; // 无记录则新建
            state.loading = false; // 异步任务已结束
            state.ready = ready; // 成功 true / 失败 false
            return state; // 写回 Map
        }); // lambda/匿名比较器结束，供排序或 stream 使用
    }

    /**
     * 按数据类型选择对应的 ConcurrentHashMap。
     */
    private Map<Long, State> states(DataType type) { // 按数据类型选择对应的 ConcurrentHashMap
        return switch (type) { // 按数据类型选择对应的 ConcurrentHashMap
            case BAG -> bagStates; // 背包预加载状态表
            case SKILL -> skillStates; // 技能预加载状态表
            case ACTIVITY -> activityStates; // 活动预加载状态表
        }; // switch 表达式结束，返回选定的业务分支结果
    }

    /** 单个玩家某类数据的加载状态快照 */
    private static final class State { // 单个玩家某类数据的加载状态快照
        /** 是否已成功预加载，true 时 BagService 等可跳过 loading 门控 */
        private boolean ready; // 按数据类型选择对应的 ConcurrentHashMap
        /** 是否已有异步任务在执行中 */
        private boolean loading; // 按数据类型选择对应的 ConcurrentHashMap
        /** 上次 tryStart 的毫秒时间戳，用于 dedupe 窗口判断 */
        private long lastStartMs; // 按数据类型选择对应的 ConcurrentHashMap

        /** 构造初始/重置状态：未 ready、未 loading、lastStartMs=0 */
        private static State newReset() { // 构造初始/重置状态：未 ready、未 loading、lastStartMs=0
            State state = new State(); // 初始状态对象
            state.ready = false; // 尚未预加载成功
            state.loading = false; // 无进行中的异步任务
            state.lastStartMs = 0L; // dedupe 窗口起点
            return state; // 供 put/compute 写入 Map
        }
    }
}
