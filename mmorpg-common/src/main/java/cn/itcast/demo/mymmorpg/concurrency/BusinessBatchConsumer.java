package cn.itcast.demo.mymmorpg.concurrency;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 无锁业务队列批量消费：IO 线程 offer，专用线程池按批 drain 执行，隔离 Netty EventLoop。
 */
@Component
public class BusinessBatchConsumer {

    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker;
    private final AtomicLong offered = new AtomicLong();
    private final AtomicLong consumed = new AtomicLong();
    private final AtomicLong batches = new AtomicLong();
    private volatile int batchSize = 64;
    private volatile boolean draining;

    public BusinessBatchConsumer() {
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "biz-batch-consumer");
            t.setDaemon(true);
            return t;
        });
    }

    public void configure(int batchSize) {
        this.batchSize = Math.max(8, Math.min(512, batchSize));
    }

    public void offer(Runnable task) {
        if (task == null) {
            return;
        }
        queue.offer(task);
        offered.incrementAndGet();
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (draining) {
            return;
        }
        draining = true;
        worker.execute(this::drainLoop);
    }

    private void drainLoop() {
        try {
            while (true) {
                List<Runnable> batch = new ArrayList<>(batchSize);
                Runnable r;
                while (batch.size() < batchSize && (r = queue.poll()) != null) {
                    batch.add(r);
                }
                if (batch.isEmpty()) {
                    break;
                }
                batches.incrementAndGet();
                for (Runnable task : batch) {
                    try {
                        task.run();
                        consumed.incrementAndGet();
                    } catch (Exception ignored) {
                        // 单条失败不阻塞批次
                    }
                }
            }
        } finally {
            draining = false;
            if (!queue.isEmpty()) {
                scheduleDrain();
            }
        }
    }

    public void drainNow(Consumer<List<Runnable>> handler) {
        List<Runnable> batch = new ArrayList<>();
        Runnable r;
        while ((r = queue.poll()) != null) {
            batch.add(r);
        }
        if (!batch.isEmpty() && handler != null) {
            handler.accept(batch);
            consumed.addAndGet(batch.size());
        }
    }

    public int pending() {
        return queue.size();
    }

    public Map<String, Object> stats() {
        return Map.of(
                "pending", queue.size(),
                "offered", offered.get(),
                "consumed", consumed.get(),
                "batches", batches.get(),
                "batchSize", batchSize);
    }

    public void shutdown() {
        worker.shutdownNow();
    }
}
