package cn.itcast.demo.mymmorpg.world.ownership;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界主权上下文：每个 CoopRoom 绑定 hostPlayerId，供采集/开箱/接任务做 Host-Only 校验。
 */
@Service
public class WorldOwnershipContext {

    public record RoomBinding(String roomId, long hostPlayerId, Set<Long> memberIds) {
        public RoomBinding {
            roomId = roomId == null ? "" : roomId.trim();
            memberIds = memberIds == null ? Set.of() : Set.copyOf(memberIds);
        }

        public boolean isHost(long playerId) {
            return playerId == hostPlayerId;
        }

        public boolean isMember(long playerId) {
            return memberIds.contains(playerId) || playerId == hostPlayerId;
        }

        public boolean isGuest(long playerId) {
            return isMember(playerId) && !isHost(playerId);
        }
    }

    private final ConcurrentHashMap<String, RoomBinding> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerRoom = new ConcurrentHashMap<>();

    public Map<String, Object> bindRoom(String roomId, long hostPlayerId, long... members) {
        String rid = roomId == null ? "" : roomId.trim();
        ConcurrentHashMap.KeySetView<Long, Boolean> set = ConcurrentHashMap.newKeySet();
        set.add(hostPlayerId);
        if (members != null) {
            for (long m : members) {
                if (m > 0) {
                    set.add(m);
                }
            }
        }
        RoomBinding binding = new RoomBinding(rid, hostPlayerId, set);
        rooms.put(rid, binding);
        for (Long pid : set) {
            playerRoom.put(pid, rid);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("roomId", rid);
        body.put("hostPlayerId", hostPlayerId);
        body.put("memberCount", set.size());
        return body;
    }

    public void unbindRoom(String roomId) {
        String rid = roomId == null ? "" : roomId.trim();
        RoomBinding old = rooms.remove(rid);
        if (old != null) {
            for (Long pid : old.memberIds()) {
                playerRoom.remove(pid, rid);
            }
        }
    }

    public void transferHost(String roomId, long newHostPlayerId) {
        String rid = roomId == null ? "" : roomId.trim();
        RoomBinding old = rooms.get(rid);
        if (old == null || newHostPlayerId <= 0) {
            return;
        }
        ConcurrentHashMap.KeySetView<Long, Boolean> set = ConcurrentHashMap.newKeySet();
        set.addAll(old.memberIds());
        set.add(newHostPlayerId);
        rooms.put(rid, new RoomBinding(rid, newHostPlayerId, set));
    }

    public RoomBinding ofRoom(String roomId) {
        return rooms.get(roomId == null ? "" : roomId.trim());
    }

    public RoomBinding ofPlayer(long playerId) {
        String rid = playerRoom.get(playerId);
        return rid == null ? null : rooms.get(rid);
    }

    public long hostOf(String roomId) {
        RoomBinding b = ofRoom(roomId);
        return b == null ? 0L : b.hostPlayerId();
    }

    /**
     * 交互准入：HOST_ONLY 仅房主；GUEST_READ 拒绝写操作；ALL_SHARE 房内成员均可。
     */
    public Map<String, Object> authorizeInteract(
            long playerId, String roomId, AccessLevel level, String action) {
        AccessLevel lv = level == null ? AccessLevel.ALL_SHARE : level;
        String act = action == null ? "interact" : action.trim();
        RoomBinding room = roomId == null || roomId.isBlank() ? ofPlayer(playerId) : ofRoom(roomId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", act);
        body.put("accessLevel", lv.name());
        body.put("playerId", playerId);

        if (room == null) {
            // 无联机房间：单人世界，视为房主
            body.put("ok", true);
            body.put("role", "SOLO_HOST");
            body.put("authorized", true);
            return body;
        }

        body.put("roomId", room.roomId());
        body.put("hostPlayerId", room.hostPlayerId());
        boolean host = room.isHost(playerId);
        boolean guest = room.isGuest(playerId);
        body.put("role", host ? "HOST" : (guest ? "GUEST" : "OUTSIDER"));

        if (!room.isMember(playerId)) {
            body.put("ok", false);
            body.put("authorized", false);
            body.put("error", "not_room_member");
            return body;
        }

        return switch (lv) {
            case HOST_ONLY -> {
                if (host) {
                    body.put("ok", true);
                    body.put("authorized", true);
                } else {
                    body.put("ok", false);
                    body.put("authorized", false);
                    body.put("error", "host_only");
                    body.put("hint", "访客不可拾取房主限定资产，防止偷世界");
                }
                yield body;
            }
            case GUEST_READ -> {
                if (host) {
                    body.put("ok", true);
                    body.put("authorized", true);
                } else {
                    body.put("ok", false);
                    body.put("authorized", false);
                    body.put("error", "guest_read_only");
                    body.put("visible", true);
                    body.put("hint", "访客可见但不可交互");
                }
                yield body;
            }
            case ALL_SHARE -> {
                body.put("ok", true);
                body.put("authorized", true);
                yield body;
            }
        };
    }

    public boolean canInteract(long playerId, String roomId, AccessLevel level) {
        return Boolean.TRUE.equals(authorizeInteract(playerId, roomId, level, "check").get("authorized"));
    }
}
