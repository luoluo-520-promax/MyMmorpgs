/**
 * MQ 消息异步适配层：从 RocketMQ 消费线程快速返回，提交到固定大小工作线程池；
 * 对「消息类型 + receiver」使用 SerialExecutor 保证同键消息严格顺序（如连续两次加金币）。
 */
package cn.itcast.demo.mymmorpg.support;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MqMessageAdapter {

    private static final Logger log = LoggerFactory.getLogger(MqMessageAdapter.class);

    private final MqMessageDispatcher dispatcher; // 实际同步调用 Handler.handle
    private final ThreadPoolExecutor workerPool; // 并行处理不同 orderingKey 的消息
    /** orderingKey（类名#receiver）-> 串行执行器，同键消息 FIFO */
    private final ConcurrentMap<String, SerialExecutor> orderedExecutors = new ConcurrentHashMap<>();

    public MqMessageAdapter(MqMessageDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        // 至少 4 线程，或 CPU 核数，避免 MQ 高峰时任务堆积
        this.workerPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                new NamedThreadFactory("mq-worker-")
        );
    }

    /**
     * 提交消息到异步流水线：无 receiver 的消息直接忽略（无法路由到具体 game-server）。
     */
    public void submit(MqMessage message) {
        if (message.receiver() == null || message.receiver().isBlank()) {
            log.warn("忽略无 receiver 的消息 messageType={}", message.getClass().getName());
            return;
        }
        String orderingKey = message.getClass().getName() + "#" + message.receiver(); // 同类型同实例有序
        SerialExecutor serialExecutor = orderedExecutors.computeIfAbsent(orderingKey, k -> new SerialExecutor(workerPool));
        serialExecutor.execute(() -> dispatcher.dispatch(message)); // 在串行队列中调用 dispatch
    }

    @PreDestroy
    public void shutdown() {
        workerPool.shutdown();
    }

    /** 为 MQ 工作线程命名 mq-worker-1、mq-worker-2… 便于监控与 dump */
    private static final class NamedThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger(1);
        private final String prefix;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + index.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * 串行执行器：同一 SerialExecutor 内任务排队，前一个完成后才调度下一个，
     * 底层 delegate 为共享线程池，不同 SerialExecutor 仍可并行。
     */
    private static final class SerialExecutor implements Executor {

        private final Queue<Runnable> tasks = new ArrayDeque<>(); // 待执行任务 FIFO 队列
        private final Executor delegate; // 实际线程池
        private Runnable active; // 当前已提交到线程池、尚未完成的任务

        private SerialExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void execute(Runnable command) {
            tasks.offer(() -> {
                try {
                    command.run();
                } finally {
                    scheduleNext(); // 当前任务结束（含异常）后尝试拉取下一个
                }
            });
            if (active == null) {
                scheduleNext(); // 队列空闲时立即启动第一条
            }
        }

        private synchronized void scheduleNext() {
            if ((active = tasks.poll()) != null) {
                delegate.execute(active); // 提交到 workerPool 执行
            }
        }
    }
}
