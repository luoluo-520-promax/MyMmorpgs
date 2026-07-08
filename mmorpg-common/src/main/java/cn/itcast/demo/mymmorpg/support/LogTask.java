/**
 * Runnable 装饰器：包装 Buff 到期、定时刷怪等调度任务，
 * 捕获 Throwable 并打日志，防止 ScheduledExecutorService 因未捕获异常终止后续调度。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger; // 记录定时任务执行异常
import org.slf4j.LoggerFactory;

import java.util.Objects; // requireNonNull 校验 delegate 非空

public final class LogTask implements Runnable { // 不可变装饰器，专用于 SchedulerManager 提交的任务

    private static final Logger log = LoggerFactory.getLogger(LogTask.class); // 本类统一 error 日志

    private final Runnable delegate; // 被包装的真实游戏逻辑（如 removeBuff、spawnMonster）

    public LogTask(Runnable delegate) {
        this.delegate = Objects.requireNonNull(delegate); // delegate 为 null 时立即失败，避免静默空跑
    }

    @Override
    public void run() {
        try {
            delegate.run(); // 执行 Buff 移除、技能 CD 结束回调、定时刷怪等
        } catch (Throwable t) { // 捕获 Error 与 Exception，ScheduledExecutorService 默认会因未捕获异常取消后续调度
            log.error("定时任务执行异常，已隔离，不影响后续任务", t); // 吞掉异常，保证 buff-scheduler 线程池继续服务其他 Buff
        }
    }
}
