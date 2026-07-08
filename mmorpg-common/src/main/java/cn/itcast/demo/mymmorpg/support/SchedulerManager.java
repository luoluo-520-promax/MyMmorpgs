/**
 * 游戏内延迟调度管理器：Buff 到期、技能 CD、定时刷怪等需「延迟 N 毫秒执行一次」，
 * 使用 2 线程守护池 + LogTask 包装，避免单次任务异常拖垮调度线程。
 */
package cn.itcast.demo.mymmorpg.support;

import jakarta.annotation.PreDestroy; // Spring 容器销毁前回调 shutdown

import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService; // 支持 schedule 延迟/周期任务
import java.util.concurrent.ScheduledFuture; // 可 cancel 的调度句柄
import java.util.concurrent.TimeUnit;

/**
 * 基于 {@link ScheduledExecutorService} 的延迟调度（2 线程池），
 * 任务经 {@link LogTask} 包装后提交；固定间隔周期由调用方在 Runnable 内再次 schedule 实现。
 */
@Component
public class SchedulerManager {

    /** 2 线程守护池：Buff 到期、场景定时事件等可并行，不阻止 JVM 退出 */
    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "buff-scheduler"); // 线程名便于 jstack 定位 Buff 相关定时任务
                t.setDaemon(true); // 守护线程，主进程结束时不阻塞
                return t;
            });

    /**
     * 延迟一次性执行（毫秒）。
     * 例如 Buff 30 秒后失效：schedule(() -> removeBuff(...), 30000)。
     *
     * @return ScheduledFuture 可用于 cancel 提前取消 Buff 到期任务
     */
    public ScheduledFuture<?> schedule(Runnable task, long delayMs) {
        return executor.schedule(new LogTask(task), delayMs, TimeUnit.MILLISECONDS); // LogTask 吞掉异常
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown(); // 优雅停止，不再接受新任务
    }
}
