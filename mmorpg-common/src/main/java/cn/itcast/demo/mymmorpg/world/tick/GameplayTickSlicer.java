package cn.itcast.demo.mymmorpg.world.tick;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/**
 * 分帧 Tick：将全局实体遍历拆成 N 片，每帧只处理 1/N，保证单次耗时 &lt; budgetMs。
 */
@Component
public class GameplayTickSlicer {

    private final AtomicInteger sliceCursor = new AtomicInteger();
    private final ExecutorService asyncPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "gameplay-async");
        t.setDaemon(true);
        return t;
    });
    private volatile int sliceCount = 20;
    private volatile long budgetMs = 5L;

    public void configure(int sliceCount, long budgetMs) {
        this.sliceCount = Math.max(4, Math.min(100, sliceCount));
        this.budgetMs = Math.max(1L, budgetMs);
    }

    /**
     * 按 entityId 哈希取模分片，仅处理当前 slice 的实体。
     */
    public <T> SliceResult processSlice(List<T> entities, java.util.function.ToLongFunction<T> idFn,
                                        BiConsumer<T, Long> handler, long nowMs) {
        int slice = Math.floorMod(sliceCursor.getAndIncrement(), sliceCount);
        long start = System.nanoTime();
        List<T> processed = new ArrayList<>();
        for (T entity : entities) {
            long id = idFn.applyAsLong(entity);
            if (Math.floorMod(id, sliceCount) != slice) {
                continue;
            }
            handler.accept(entity, nowMs);
            processed.add(entity);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs >= budgetMs) {
                break;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        return new SliceResult(slice, processed.size(), elapsedMs, elapsedMs < budgetMs);
    }

    /**
     * 非强一致逻辑异步执行（生态 AI、图鉴统计、纪元结算等）。
     */
    public Future<?> runAsync(Runnable task) {
        return asyncPool.submit(task);
    }

    public void runAsyncFireAndForget(LongConsumer tickFn, long nowMs) {
        asyncPool.execute(() -> tickFn.accept(nowMs));
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sliceCount", sliceCount);
        m.put("currentSlice", Math.floorMod(sliceCursor.get(), sliceCount));
        m.put("budgetMs", budgetMs);
        return m;
    }

    public record SliceResult(int sliceIndex, int processedCount, long elapsedMs, boolean withinBudget) {
    }

    public void shutdown() {
        asyncPool.shutdownNow();
    }
}
