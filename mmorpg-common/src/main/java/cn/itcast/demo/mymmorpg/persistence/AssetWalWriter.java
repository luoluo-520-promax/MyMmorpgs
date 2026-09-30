package cn.itcast.demo.mymmorpg.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 资产变更 WAL：关键变更先顺序追加本地日志，再由消费者异步回写 MySQL，主链路释放锁等待。
 */
@Component
public class AssetWalWriter {

    private static final Logger log = LoggerFactory.getLogger(AssetWalWriter.class);

    public record WalEntry(long seq, long playerId, String opType, String payloadJson, long tsMs) {
    }

    private final ConcurrentLinkedQueue<WalEntry> memoryWal = new ConcurrentLinkedQueue<>();
    private final AtomicLong seq = new AtomicLong();
    private final AtomicLong appended = new AtomicLong();
    private final AtomicLong consumed = new AtomicLong();
    private volatile Path walPath;
    private volatile int maxMemoryEntries = 10_000;

    public void configure(Path walPath, int maxMemoryEntries) {
        this.walPath = walPath;
        this.maxMemoryEntries = Math.max(1000, maxMemoryEntries);
    }

    public WalEntry append(long playerId, String opType, String payloadJson, long tsMs) {
        long s = seq.incrementAndGet();
        WalEntry entry = new WalEntry(s, playerId, opType, payloadJson == null ? "{}" : payloadJson, tsMs);
        if (memoryWal.size() < maxMemoryEntries) {
            memoryWal.offer(entry);
        }
        appended.incrementAndGet();
        if (walPath != null) {
            try {
                String line = s + "\t" + playerId + "\t" + opType + "\t" + tsMs + "\t" + entry.payloadJson() + "\n";
                Files.writeString(walPath, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warn("WAL append failed: {}", e.getMessage());
            }
        }
        return entry;
    }

    public List<WalEntry> drain(int maxBatch) {
        List<WalEntry> batch = new ArrayList<>(Math.min(maxBatch, memoryWal.size()));
        WalEntry e;
        while (batch.size() < maxBatch && (e = memoryWal.poll()) != null) {
            batch.add(e);
            consumed.incrementAndGet();
        }
        return batch;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("memoryPending", memoryWal.size());
        m.put("appended", appended.get());
        m.put("consumed", consumed.get());
        m.put("lastSeq", seq.get());
        m.put("walPath", walPath == null ? null : walPath.toString());
        return m;
    }
}
