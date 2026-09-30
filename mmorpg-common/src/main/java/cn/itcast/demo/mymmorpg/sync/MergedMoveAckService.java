package cn.itcast.demo.mymmorpg.sync;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 合并移动 ACK：客户端累计 3 帧后再上行，减少包数。
 */
@Component
public class MergedMoveAckService {

    public record PendingAck(long playerId, long serverSeq, float x, float y, float z, long clientTs) {
    }

    private static final int MERGE_FRAMES = 3;

    private final ConcurrentHashMap<Long, List<PendingAck>> pending = new ConcurrentHashMap<>();
    private final AtomicLong acksReceived = new AtomicLong();
    private final AtomicLong acksMerged = new AtomicLong();
    private volatile int mergeFrames = MERGE_FRAMES;

    public void configure(int mergeFrames) {
        this.mergeFrames = Math.max(1, mergeFrames);
    }

    /**
     * 接收单帧 ACK，满 mergeFrames 后返回合并结果（仅保留最后一帧坐标）。
     */
    public Map<String, Object> receiveAck(long playerId, long serverSeq,
                                          float x, float y, float z, long clientTs) {
        acksReceived.incrementAndGet();
        List<PendingAck> batch = pending.computeIfAbsent(playerId, id -> new ArrayList<>(mergeFrames));
        synchronized (batch) {
            batch.add(new PendingAck(playerId, serverSeq, x, y, z, clientTs));
            if (batch.size() < mergeFrames) {
                return Map.of("ok", true, "merged", false, "pending", batch.size());
            }
            PendingAck last = batch.get(batch.size() - 1);
            batch.clear();
            acksMerged.incrementAndGet();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("merged", true);
            body.put("playerId", playerId);
            body.put("serverSeq", last.serverSeq());
            body.put("x", last.x());
            body.put("y", last.y());
            body.put("z", last.z());
            body.put("clientTs", last.clientTs());
            body.put("framesMerged", mergeFrames);
            return body;
        }
    }

    public void reset(long playerId) {
        pending.remove(playerId);
    }

    public Map<String, Object> stats() {
        return Map.of(
                "pendingPlayers", pending.size(),
                "acksReceived", acksReceived.get(),
                "acksMerged", acksMerged.get(),
                "mergeFrames", mergeFrames);
    }
}
