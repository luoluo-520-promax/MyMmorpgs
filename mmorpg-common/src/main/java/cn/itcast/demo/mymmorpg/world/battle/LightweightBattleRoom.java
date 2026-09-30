package cn.itcast.demo.mymmorpg.world.battle;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 跨服副本「轻量小场景」：房间内点对点广播，不经大世界 Zone/AOI 管理器，避免全服广播风暴。
 */
@Component
public class LightweightBattleRoom {

    public enum RoomStatus { CREATING, READY, ACTIVE, CLOSED }

    public record Room(
            String roomId,
            int sceneTemplateId,
            List<Long> memberIds,
            RoomStatus status,
            long createdAtMs,
            long readyAtMs) {
    }

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<byte[]>> pendingBroadcasts = new ConcurrentHashMap<>();
    private final AtomicLong broadcasts = new AtomicLong();

    public Room create(int sceneTemplateId, List<Long> memberIds, long nowMs) {
        String roomId = "lbr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<Long> members = memberIds == null ? List.of() : List.copyOf(memberIds);
        Room room = new Room(roomId, sceneTemplateId, members, RoomStatus.CREATING, nowMs, 0L);
        rooms.put(roomId, room);
        for (Long pid : members) {
            playerRoom.put(pid, roomId);
        }
        pendingBroadcasts.put(roomId, new ArrayList<>());
        return room;
    }

    public Room markReady(String roomId, long nowMs) {
        Room cur = rooms.get(roomId);
        if (cur == null) {
            return null;
        }
        Room next = new Room(cur.roomId(), cur.sceneTemplateId(), cur.memberIds(),
                RoomStatus.READY, cur.createdAtMs(), nowMs);
        rooms.put(roomId, next);
        return next;
    }

    public Room activate(String roomId) {
        Room cur = rooms.get(roomId);
        if (cur == null) {
            return null;
        }
        Room next = new Room(cur.roomId(), cur.sceneTemplateId(), cur.memberIds(),
                RoomStatus.ACTIVE, cur.createdAtMs(), cur.readyAtMs());
        rooms.put(roomId, next);
        return next;
    }

    /**
     * 房间内点对点：返回应接收 payload 的成员（不含 sender）。
     */
    public List<Long> peerBroadcastTargets(String roomId, long senderId) {
        Room room = rooms.get(roomId);
        if (room == null || room.status() == RoomStatus.CLOSED) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Long id : room.memberIds()) {
            if (id != senderId) {
                out.add(id);
            }
        }
        broadcasts.incrementAndGet();
        return out;
    }

    public void enqueuePayload(String roomId, byte[] payload) {
        List<byte[]> q = pendingBroadcasts.get(roomId);
        if (q != null && payload != null) {
            synchronized (q) {
                q.add(payload);
            }
        }
    }

    public List<byte[]> drainPayloads(String roomId) {
        List<byte[]> q = pendingBroadcasts.get(roomId);
        if (q == null) {
            return List.of();
        }
        synchronized (q) {
            List<byte[]> out = List.copyOf(q);
            q.clear();
            return out;
        }
    }

    public String roomOf(long playerId) {
        return playerRoom.get(playerId);
    }

    public Room get(String roomId) {
        return rooms.get(roomId);
    }

    public void close(String roomId) {
        Room cur = rooms.get(roomId);
        if (cur == null) {
            return;
        }
        rooms.put(roomId, new Room(cur.roomId(), cur.sceneTemplateId(), cur.memberIds(),
                RoomStatus.CLOSED, cur.createdAtMs(), cur.readyAtMs()));
        for (Long pid : cur.memberIds()) {
            playerRoom.remove(pid, roomId);
        }
        pendingBroadcasts.remove(roomId);
    }

    public Map<String, Object> toView(Room room) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomId", room.roomId());
        m.put("sceneTemplateId", room.sceneTemplateId());
        m.put("memberIds", room.memberIds());
        m.put("status", room.status().name());
        m.put("createdAtMs", room.createdAtMs());
        m.put("readyAtMs", room.readyAtMs());
        m.put("readyLatencyMs", room.readyAtMs() <= 0 ? -1L : Math.max(0L, room.readyAtMs() - room.createdAtMs()));
        return m;
    }

    public Map<String, Object> stats() {
        return Map.of(
                "rooms", rooms.size(),
                "broadcasts", broadcasts.get(),
                "playersMapped", playerRoom.size());
    }
}
