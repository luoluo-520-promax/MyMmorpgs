package cn.itcast.demo.mymmorpg.world.scene;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 战斗场景 Pod 分配器：战斗副本/攻城与「大世界探索」物理进程隔离。
 * 每个 Battle-Scene-Pod 仅承载 1 个 Boss 房间（≤4 人或 ≤20 人），采用内存快照同步（无 Redis 依赖）。
 */
@Component
public class BattleScenePodAllocator {

    public enum SceneClass {
        /** 大世界跑图/采集/解密：大进程，允许 100ms 延迟 */
        OPEN_WORLD,
        /** 战斗副本/攻城：轻量 Pod，低延迟 */
        BATTLE_INSTANCE
    }

    public record BattlePod(
            String podId,
            String nodeId,
            int sceneTemplateId,
            int maxPlayers,
            int playerCount,
            SceneClass sceneClass,
            long createdAtMs,
            long expireAtMs,
            boolean memorySnapshotOnly) {
    }

    public record MemorySnapshot(
            String podId,
            long revision,
            byte[] stateBlob,
            long capturedAtMs) {
    }

    private static final int BOSS_ROOM_MAX_PLAYERS = 4;
    private static final int SIEGE_ROOM_MAX_PLAYERS = 20;

    private final ConcurrentHashMap<String, BattlePod> pods = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MemorySnapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);
    private final AtomicInteger openWorldPods = new AtomicInteger();
    private final AtomicInteger battlePods = new AtomicInteger();
    private final String openWorldNodeId;
    private final String battleNodePrefix;

    public BattleScenePodAllocator() {
        this("scene-openworld-main", "battle-pod-");
    }

    public BattleScenePodAllocator(String openWorldNodeId, String battleNodePrefix) {
        this.openWorldNodeId = openWorldNodeId == null ? "scene-openworld-main" : openWorldNodeId;
        this.battleNodePrefix = battleNodePrefix == null ? "battle-pod-" : battleNodePrefix;
    }

    /**
     * 分配战斗 Pod：独立轻量进程，内存快照同步。
     */
    public BattlePod allocateBattlePod(int sceneTemplateId, boolean siegeMode, long ttlMs, long nowMs) {
        int cap = siegeMode ? SIEGE_ROOM_MAX_PLAYERS : BOSS_ROOM_MAX_PLAYERS;
        String podId = "bp-" + seq.getAndIncrement() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String nodeId = battleNodePrefix + podId;
        BattlePod pod = new BattlePod(
                podId, nodeId, sceneTemplateId, cap, 0,
                SceneClass.BATTLE_INSTANCE, nowMs, nowMs + Math.max(60_000L, ttlMs), true);
        pods.put(podId, pod);
        battlePods.incrementAndGet();
        return pod;
    }

    /**
     * 大世界场景：维持大进程，非 Redis 强依赖。
     */
    public BattlePod registerOpenWorld(int sceneTemplateId, int estimatedPlayers, long nowMs) {
        String podId = "ow-main-" + sceneTemplateId;
        BattlePod pod = new BattlePod(
                podId, openWorldNodeId, sceneTemplateId,
                Math.max(estimatedPlayers, 200), estimatedPlayers,
                SceneClass.OPEN_WORLD, nowMs, nowMs + 86_400_000L, false);
        pods.put(podId, pod);
        openWorldPods.incrementAndGet();
        return pod;
    }

    public MemorySnapshot captureSnapshot(String podId, byte[] stateBlob, long nowMs) {
        long rev = snapshots.containsKey(podId)
                ? snapshots.get(podId).revision() + 1
                : 1L;
        MemorySnapshot snap = new MemorySnapshot(podId, rev, stateBlob, nowMs);
        snapshots.put(podId, snap);
        return snap;
    }

    public MemorySnapshot latestSnapshot(String podId) {
        return snapshots.get(podId);
    }

    public boolean admitPlayer(String podId) {
        BattlePod pod = pods.get(podId);
        if (pod == null || pod.playerCount() >= pod.maxPlayers()) {
            return false;
        }
        BattlePod updated = new BattlePod(
                pod.podId(), pod.nodeId(), pod.sceneTemplateId(),
                pod.maxPlayers(), pod.playerCount() + 1,
                pod.sceneClass(), pod.createdAtMs(), pod.expireAtMs(), pod.memorySnapshotOnly());
        pods.put(podId, updated);
        return true;
    }

    public List<BattlePod> listBattlePods() {
        List<BattlePod> out = new ArrayList<>();
        for (BattlePod p : pods.values()) {
            if (p.sceneClass() == SceneClass.BATTLE_INSTANCE) {
                out.add(p);
            }
        }
        return out;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalPods", pods.size());
        m.put("openWorldPods", openWorldPods.get());
        m.put("battlePods", battlePods.get());
        m.put("snapshots", snapshots.size());
        m.put("openWorldNodeId", openWorldNodeId);
        m.put("bossRoomMaxPlayers", BOSS_ROOM_MAX_PLAYERS);
        m.put("siegeRoomMaxPlayers", SIEGE_ROOM_MAX_PLAYERS);
        return m;
    }
}
