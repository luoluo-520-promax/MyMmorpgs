package cn.itcast.demo.mymmorpg.physics;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 物理审计异步线程池：validate 不阻塞主 Tick，偏差超阈值则 SOFT_ROLLBACK 异步消息。
 */
@Component
public class AsyncPhysicsThreadPool {

    private final ExecutorService executor;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong softRollbacks = new AtomicLong();

    public AsyncPhysicsThreadPool() {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "async-physics-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2), factory);
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        submitted.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get();
            } finally {
                completed.incrementAndGet();
            }
        }, executor);
    }

    public void onSoftRollback() {
        softRollbacks.incrementAndGet();
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("submitted", submitted.get());
        m.put("completed", completed.get());
        m.put("softRollbacks", softRollbacks.get());
        return m;
    }
}
