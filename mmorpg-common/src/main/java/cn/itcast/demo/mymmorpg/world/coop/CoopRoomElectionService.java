package cn.itcast.demo.mymmorpg.world.coop;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.sync.ServerShadowService;
import cn.itcast.demo.mymmorpg.world.ownership.WorldOwnershipContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 联机房主选举：房主断线超阈值后，按 Ping 最低 + SceneActor 负载选举新 Host，并广播 HOST_TRANSFER。
 */
@Service
public class CoopRoomElectionService {

    public static final long HOST_DISCONNECT_THRESHOLD_MS = 30_000L;
    public static final String LOCK_PREFIX = "coop:host_election:";

    public record Candidate(long playerId, long pingMs, int sceneActorLoad) {
    }

    public record ElectionLock(String roomId, String lockToken, long lockedAtMs) {
    }

    private final ConcurrentHashMap<String, Long> hostDisconnectAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ElectionLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> currentHost = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Candidate>> candidates = new ConcurrentHashMap<>();

    private WorldOwnershipContext ownership;
    private ServerShadowService shadows;
    private CoopTerrainSnapshotService terrainSnapshot;

    public CoopRoomElectionService() {
    }

    public CoopRoomElectionService(WorldOwnershipContext ownership, ServerShadowService shadows) {
        this.ownership = ownership;
        this.shadows = shadows;
    }

    public void bind(WorldOwnershipContext ownership, ServerShadowService shadows) {
        this.ownership = ownership;
        this.shadows = shadows;
    }

    public void bindTerrainSnapshot(CoopTerrainSnapshotService snapshot) {
        this.terrainSnapshot = snapshot;
    }

    public void registerHost(String roomId, long hostPlayerId) {
        String rid = norm(roomId);
        currentHost.put(rid, hostPlayerId);
        hostDisconnectAt.remove(rid);
    }

    public void updateCandidates(String roomId, List<Candidate> list) {
        candidates.put(norm(roomId), list == null ? List.of() : List.copyOf(list));
    }

    /**
     * 房主断线瞬间：打点并尝试抢 Redis 分布式锁（内存模拟）。
     */
    public Map<String, Object> onHostDisconnect(String roomId, long hostPlayerId, long nowMs) {
        String rid = norm(roomId);
        hostDisconnectAt.put(rid, nowMs);
        String token = rid + ":" + nowMs + ":" + Thread.currentThread().getId();
        ElectionLock existing = locks.get(rid);
        boolean acquired = false;
        if (existing == null) {
            locks.put(rid, new ElectionLock(rid, token, nowMs));
            acquired = true;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("roomId", rid);
        body.put("hostPlayerId", hostPlayerId);
        body.put("disconnectAtMs", nowMs);
        body.put("thresholdMs", HOST_DISCONNECT_THRESHOLD_MS);
        body.put("lockKey", LOCK_PREFIX + rid);
        body.put("lockAcquired", acquired);
        body.put("lockToken", acquired ? token : (existing == null ? "" : existing.lockToken()));
        body.put("pendingElection", true);
        return body;
    }

    /**
     * 超过 30s 仍未重连 → 选举新 Host，加载 roomSnapshot，广播 HOST_TRANSFER_SC_NOTIFY。
     */
    public Map<String, Object> tryElect(String roomId, long nowMs) {
        String rid = norm(roomId);
        Long disc = hostDisconnectAt.get(rid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomId", rid);
        if (disc == null) {
            body.put("ok", false);
            body.put("error", "host_not_disconnected");
            return body;
        }
        long elapsed = nowMs - disc;
        body.put("disconnectElapsedMs", elapsed);
        if (elapsed < HOST_DISCONNECT_THRESHOLD_MS) {
            body.put("ok", true);
            body.put("elected", false);
            body.put("waitingMs", HOST_DISCONNECT_THRESHOLD_MS - elapsed);
            return body;
        }

        Long oldHost = currentHost.get(rid);
        List<Candidate> pool = new ArrayList<>(candidates.getOrDefault(rid, List.of()));
        pool.removeIf(c -> oldHost != null && c.playerId() == oldHost);
        if (pool.isEmpty()) {
            body.put("ok", false);
            body.put("elected", false);
            body.put("error", "no_candidate");
            body.put("dissolveRoom", true);
            return body;
        }

        pool.sort(Comparator
                .comparingLong(Candidate::pingMs)
                .thenComparingInt(Candidate::sceneActorLoad));
        Candidate winner = pool.get(0);
        currentHost.put(rid, winner.playerId());
        hostDisconnectAt.remove(rid);
        locks.remove(rid);

        if (ownership != null) {
            ownership.transferHost(rid, winner.playerId());
        }

        Map<String, Object> snapshot = Map.of();
        if (shadows != null) {
            snapshot = shadows.loadRoomSnapshot(rid);
        }

        Map<String, Object> terrainSnap = Map.of();
        Map<String, Object> recompose = Map.of();
        if (terrainSnapshot != null) {
            terrainSnap = terrainSnapshot.loadView(rid, nowMs);
            recompose = terrainSnapshot.awaitWorldRecompose(rid, nowMs);
        }

        body.put("ok", true);
        body.put("elected", true);
        body.put("oldHostPlayerId", oldHost == null ? 0L : oldHost);
        body.put("newHostPlayerId", winner.playerId());
        body.put("winnerPingMs", winner.pingMs());
        body.put("winnerLoad", winner.sceneActorLoad());
        body.put("msgId", MessageId.HOST_TRANSFER_SC_NOTIFY);
        body.put("event", "HOST_TRANSFER_SC_NOTIFY");
        body.put("roomSnapshot", snapshot);
        body.put("terrainCoopSnapshot", terrainSnap);
        body.put("worldRecompose", recompose);
        body.put("seamless", true);
        body.put("reloadScene", false);
        body.put("rejectInstantReconnect", true);
        body.put("hint", "新 Host 从 Redis 加载 terrain:coop:snapshot，剔除已毁物件后允许重连");
        return body;
    }

    public Map<String, Object> onHostReconnect(String roomId, long hostPlayerId) {
        String rid = norm(roomId);
        Long current = currentHost.get(rid);
        hostDisconnectAt.remove(rid);
        locks.remove(rid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("roomId", rid);
        body.put("hostPlayerId", hostPlayerId);
        body.put("stillHost", current != null && current == hostPlayerId);
        body.put("electionCancelled", true);
        return body;
    }

    public long currentHostOf(String roomId) {
        return currentHost.getOrDefault(norm(roomId), 0L);
    }

    private static String norm(String roomId) {
        return roomId == null ? "" : roomId.trim();
    }
}
