package cn.itcast.demo.mymmorpg.world.scene;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.world.battle.LightweightBattleRoom;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 场景后台预创建：匹配成功瞬间在目标战斗服预分配实例并加载资源，客户端收到传送时直接进入 READY，压缩黑屏至 ~500ms。
 */
@Component
public class SceneBackgroundPrecreator {

    public enum PrepStatus { ALLOCATING, LOADING, READY, CONSUMED, EXPIRED, FAILED }

    public record PrepHandle(
            String prepId,
            String instanceId,
            String roomId,
            int sceneId,
            int lineId,
            String nodeId,
            String host,
            int port,
            List<Long> playerIds,
            Map<Long, String> tickets,
            PrepStatus status,
            long createdAtMs,
            long readyAtMs,
            long expireAtMs) {
    }

    private final K8sSceneAllocator allocator;
    private final LightweightBattleRoom battleRooms;
    private final MigrationTicketService tickets;
    private final ConcurrentHashMap<String, PrepHandle> handles = new ConcurrentHashMap<>();
    private final AtomicLong readyCount = new AtomicLong();
    private volatile long defaultTtlMs = 30_000L;
    private volatile long targetReadyBudgetMs = 500L;

    public SceneBackgroundPrecreator() {
        this(new K8sSceneAllocator(), new LightweightBattleRoom(), new MigrationTicketService());
    }

    public SceneBackgroundPrecreator(
            K8sSceneAllocator allocator,
            LightweightBattleRoom battleRooms,
            MigrationTicketService tickets) {
        this.allocator = allocator == null ? new K8sSceneAllocator() : allocator;
        this.battleRooms = battleRooms == null ? new LightweightBattleRoom() : battleRooms;
        this.tickets = tickets == null ? new MigrationTicketService() : tickets;
    }

    public void configure(long ttlMs, long readyBudgetMs) {
        this.defaultTtlMs = Math.max(5_000L, ttlMs);
        this.targetReadyBudgetMs = Math.max(100L, readyBudgetMs);
    }

    /**
     * 匹配成功后调用：分配节点实例 + 轻量房间 + 全员 seamless 票据，状态置 READY。
     */
    public PrepHandle prepareCrossDungeon(
            int sceneId,
            int lineId,
            int capacity,
            List<Long> playerIds,
            long nowMs) {
        // 确保至少有一个本地节点可供演示/单测
        if (allocator.listNodes().isEmpty()) {
            allocator.registerOrHeartbeat(new K8sSceneAllocator.SceneNode(
                    "scene-local", "127.0.0.1", 8082, 200, 512, 0, 200, true, nowMs), nowMs);
        }
        Map<String, Object> alloc = allocator.allocateOnBestNode(sceneId, capacity, defaultTtlMs, nowMs);
        if (!Boolean.TRUE.equals(alloc.get("ok"))) {
            String prepId = "prep-fail-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            PrepHandle failed = new PrepHandle(
                    prepId, "", "", sceneId, lineId, "", "", 0,
                    playerIds == null ? List.of() : List.copyOf(playerIds),
                    Map.of(), PrepStatus.FAILED, nowMs, 0L, nowMs + defaultTtlMs);
            handles.put(prepId, failed);
            return failed;
        }
        String instanceId = String.valueOf(alloc.get("instanceId"));
        String nodeId = String.valueOf(alloc.get("nodeId"));
        String host = String.valueOf(alloc.get("host"));
        int port = ((Number) alloc.get("port")).intValue();

        List<Long> members = playerIds == null ? List.of() : List.copyOf(playerIds);
        LightweightBattleRoom.Room room = battleRooms.create(sceneId, members, nowMs);
        battleRooms.markReady(room.roomId(), nowMs);

        Map<Long, String> ticketMap = new LinkedHashMap<>();
        for (Long pid : members) {
            ticketMap.put(pid, tickets.issueSeamless(
                    pid, sceneId, lineId, 0, 0f, 0f, 0f, 0f, 0f, 0f, 0));
        }

        // 模拟资源已加载完毕（真实环境由目标 Scene 回调 markReady）
        long readyAt = nowMs + Math.min(targetReadyBudgetMs, 400L);
        String prepId = "prep-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        PrepHandle handle = new PrepHandle(
                prepId, instanceId, room.roomId(), sceneId, lineId,
                nodeId, host, port, members, Map.copyOf(ticketMap),
                PrepStatus.READY, nowMs, readyAt, nowMs + defaultTtlMs);
        handles.put(prepId, handle);
        readyCount.incrementAndGet();
        allocator.pool().heartbeat(instanceId, nowMs, defaultTtlMs);
        return handle;
    }

    public PrepHandle consume(String prepId, long nowMs) {
        PrepHandle cur = handles.get(prepId);
        if (cur == null) {
            return null;
        }
        if (cur.expireAtMs() <= nowMs) {
            PrepHandle expired = withStatus(cur, PrepStatus.EXPIRED, cur.readyAtMs());
            handles.put(prepId, expired);
            return expired;
        }
        PrepHandle consumed = withStatus(cur, PrepStatus.CONSUMED, cur.readyAtMs());
        handles.put(prepId, consumed);
        if (cur.roomId() != null && !cur.roomId().isBlank()) {
            battleRooms.activate(cur.roomId());
        }
        return consumed;
    }

    public PrepHandle get(String prepId) {
        return handles.get(prepId);
    }

    public Map<String, Object> toClientHandoff(PrepHandle h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", h.status() == PrepStatus.READY || h.status() == PrepStatus.CONSUMED);
        m.put("prepId", h.prepId());
        m.put("instanceId", h.instanceId());
        m.put("roomId", h.roomId());
        m.put("sceneId", h.sceneId());
        m.put("lineId", h.lineId());
        m.put("nodeId", h.nodeId());
        m.put("host", h.host());
        m.put("port", h.port());
        m.put("status", h.status().name());
        m.put("preloadReady", h.status() == PrepStatus.READY || h.status() == PrepStatus.CONSUMED);
        m.put("readyLatencyMs", Math.max(0L, h.readyAtMs() - h.createdAtMs()));
        m.put("targetReadyBudgetMs", targetReadyBudgetMs);
        m.put("tickets", h.tickets());
        m.put("playerIds", h.playerIds());
        return m;
    }

    public List<PrepHandle> listReady() {
        List<PrepHandle> out = new ArrayList<>();
        for (PrepHandle h : handles.values()) {
            if (h.status() == PrepStatus.READY) {
                out.add(h);
            }
        }
        return out;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tracked", handles.size());
        m.put("readyCount", readyCount.get());
        m.put("targetReadyBudgetMs", targetReadyBudgetMs);
        m.put("allocator", allocator.stats());
        m.put("battleRooms", battleRooms.stats());
        return m;
    }

    public K8sSceneAllocator allocator() {
        return allocator;
    }

    public LightweightBattleRoom battleRooms() {
        return battleRooms;
    }

    private static PrepHandle withStatus(PrepHandle cur, PrepStatus status, long readyAtMs) {
        return new PrepHandle(
                cur.prepId(), cur.instanceId(), cur.roomId(), cur.sceneId(), cur.lineId(),
                cur.nodeId(), cur.host(), cur.port(), cur.playerIds(), cur.tickets(),
                status, cur.createdAtMs(), readyAtMs, cur.expireAtMs());
    }
}
