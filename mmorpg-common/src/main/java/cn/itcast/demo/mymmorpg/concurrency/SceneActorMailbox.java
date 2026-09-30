package cn.itcast.demo.mymmorpg.concurrency;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 轻量 Actor 信箱：每个场景/玩家独立队列 + 单线程消费，避免 synchronized 争抢。
 * IO 线程仅 enqueue，业务在专用 stripe 线程串行处理。
 */
@Component
public class SceneActorMailbox {

    public record Envelope(long actorKey, Runnable task, long enqueuedAtMs) {
    }

    private final ConcurrentHashMap<Long, LinkedBlockingQueue<Envelope>> mailboxes = new ConcurrentHashMap<>();
    private final ExecutorService[] stripes;
    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile int maxQueueDepth = 2048;

    public SceneActorMailbox() {
        int n = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.stripes = new ExecutorService[n];
        for (int i = 0; i < n; i++) {
            final int stripe = i;
            stripes[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "scene-actor-" + stripe);
                t.setDaemon(true);
                return t;
            });
        }
    }

    public void configure(int maxQueueDepth) {
        this.maxQueueDepth = Math.max(4, Math.min(8192, maxQueueDepth));
    }

    /**
     * 投递消息到 actorKey 对应信箱；队列满时丢弃并计数。
     */
    public boolean tell(long actorKey, Runnable task) {
        if (task == null) {
            return false;
        }
        LinkedBlockingQueue<Envelope> q = mailboxes.computeIfAbsent(actorKey, k -> new LinkedBlockingQueue<>());
        if (q.size() >= maxQueueDepth) {
            dropped.incrementAndGet();
            return false;
        }
        Envelope env = new Envelope(actorKey, task, System.currentTimeMillis());
        if (!q.offer(env)) {
            dropped.incrementAndGet();
            return false;
        }
        enqueued.incrementAndGet();
        int idx = (int) Math.floorMod(actorKey, stripes.length);
        stripes[idx].execute(() -> drain(actorKey, q));
        return true;
    }

    private void drain(long actorKey, LinkedBlockingQueue<Envelope> q) {
        Envelope env;
        while ((env = q.poll()) != null) {
            try {
                env.task().run();
                processed.incrementAndGet();
            } catch (Exception ignored) {
                // 单条失败不阻塞信箱
            }
        }
        if (q.isEmpty()) {
            mailboxes.remove(actorKey, q);
        }
    }

    /**
     * 批量 drain：供 BusinessBatchConsumer 合并消费。
     */
    public List<Envelope> drainBatch(long actorKey, int maxBatch) {
        LinkedBlockingQueue<Envelope> q = mailboxes.get(actorKey);
        if (q == null || q.isEmpty()) {
            return List.of();
        }
        List<Envelope> batch = new ArrayList<>(Math.min(maxBatch, q.size()));
        q.drainTo(batch, maxBatch);
        if (q.isEmpty()) {
            mailboxes.remove(actorKey, q);
        }
        return batch;
    }

    public void forEachMailbox(Consumer<Map.Entry<Long, LinkedBlockingQueue<Envelope>>> action) {
        mailboxes.entrySet().forEach(action);
    }

    public Map<String, Object> stats() {
        int pending = mailboxes.values().stream().mapToInt(LinkedBlockingQueue::size).sum();
        return Map.of(
                "mailboxCount", mailboxes.size(),
                "pending", pending,
                "enqueued", enqueued.get(),
                "processed", processed.get(),
                "dropped", dropped.get(),
                "stripeCount", stripes.length);
    }

    public void shutdown() {
        for (ExecutorService es : stripes) {
            es.shutdownNow();
        }
    }
}
