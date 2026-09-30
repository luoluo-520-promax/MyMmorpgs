package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 奇观全解锁 → 「世界之核」4 人联机副本（区别于深渊），验证练度+探索双毕业。
 */
@Service
public class WorldCoreDungeonService {

    public static final String DUNGEON_ID = "WORLD_CORE";
    public static final int MAX_MEMBERS = 4;

    private final LandmarkWonderService landmarks;
    private final ConcurrentHashMap<Long, Set<String>> completedWonders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerRoom = new ConcurrentHashMap<>();

    public WorldCoreDungeonService() {
        this(new LandmarkWonderService());
    }

    public WorldCoreDungeonService(LandmarkWonderService landmarks) {
        this.landmarks = landmarks == null ? new LandmarkWonderService() : landmarks;
    }

    public LandmarkWonderService landmarks() {
        return landmarks;
    }

    public void markWonderComplete(long playerId, String landmarkId) {
        completedWonders
                .computeIfAbsent(playerId, id -> ConcurrentHashMap.newKeySet())
                .add(landmarkId);
    }

    public boolean allWondersUnlocked(long playerId) {
        int total = landmarks.list().size();
        if (total <= 0) {
            return false;
        }
        Set<String> done = completedWonders.getOrDefault(playerId, Set.of());
        return done.size() >= total;
    }

    public Map<String, Object> unlockStatus(long playerId) {
        int total = landmarks.list().size();
        int done = completedWonders.getOrDefault(playerId, Set.of()).size();
        boolean unlocked = total > 0 && done >= total;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("dungeonId", DUNGEON_ID);
        body.put("wonderTotal", total);
        body.put("wonderDone", done);
        body.put("unlocked", unlocked);
        body.put("differsFrom", "ABYSS_P8");
        body.put("coopMax", MAX_MEMBERS);
        return body;
    }

    /**
     * 创建世界之核 CoopRoom（4 人）。
     */
    public Map<String, Object> createCoopRoom(long leaderId, long nowMs) {
        if (!allWondersUnlocked(leaderId)) {
            return Map.of("ok", false, "error", "wonders_incomplete",
                    "status", unlockStatus(leaderId));
        }
        String roomId = "world-core-" + UUID.randomUUID();
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("roomId", roomId);
        room.put("dungeonId", DUNGEON_ID);
        room.put("leaderId", leaderId);
        room.put("members", java.util.List.of(leaderId));
        room.put("maxMembers", MAX_MEMBERS);
        room.put("createdAtMs", nowMs);
        room.put("mode", "COOP_4");
        room.put("requirement", "exploration_and_build");
        rooms.put(roomId, room);
        playerRoom.put(leaderId, roomId);
        Map<String, Object> body = new LinkedHashMap<>(room);
        body.put("ok", true);
        return body;
    }

    public Map<String, Object> join(long playerId, String roomId) {
        Map<String, Object> room = rooms.get(roomId);
        if (room == null) {
            return Map.of("ok", false, "error", "room_not_found");
        }
        @SuppressWarnings("unchecked")
        java.util.List<Long> members = new java.util.ArrayList<>(
                (java.util.List<Long>) room.get("members"));
        if (members.size() >= MAX_MEMBERS) {
            return Map.of("ok", false, "error", "room_full");
        }
        if (!members.contains(playerId)) {
            members.add(playerId);
        }
        room.put("members", members);
        playerRoom.put(playerId, roomId);
        return Map.of("ok", true, "roomId", roomId, "members", members, "dungeonId", DUNGEON_ID);
    }
}
