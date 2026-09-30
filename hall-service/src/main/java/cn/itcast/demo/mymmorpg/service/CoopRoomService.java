package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 联机房间（CoopRoom）：最多 4 人半单机世界；世界 BOSS 在房间内共斗。
 * 扩展：表情/动作/快捷短语、观战、点赞与送花。
 */
@Service
public class CoopRoomService {

    public static final int MAX_MEMBERS = 4;
    public static final int MAX_SPECTATORS = 20;
    public static final int MAX_FEED = 50;

    public enum RoomStatus { OPEN, FULL, IN_BOSS, CLOSED }

    public record Member(long playerId, long joinedAtMs) {
    }

    public record CoopRoom(
            String roomId,
            String bossEventId,
            long leaderId,
            RoomStatus status,
            List<Member> members,
            long bossHp,
            long bossHpMax,
            long createdAtMs) {
    }

    private final ConcurrentHashMap<String, CoopRoom> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AtomicLong>> roomDamage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> spectators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> spectatorRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> feed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AtomicLong>> likes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AtomicLong>> flowers = new ConcurrentHashMap<>();
    private final SocialEventPublisher socialEventPublisher;

    public CoopRoomService(SocialEventPublisher socialEventPublisher) {
        this.socialEventPublisher = socialEventPublisher;
    }

    public CoopRoom create(long leaderId, String bossEventId, long bossHpMax) {
        if (leaderId <= 0) {
            throw new IllegalArgumentException("invalid leader");
        }
        leaveIfAny(leaderId);
        leaveSpectateIfAny(leaderId);
        String id = "coop-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long hp = Math.max(1L, bossHpMax <= 0 ? 1_000_000L : bossHpMax);
        long now = System.currentTimeMillis();
        CoopRoom room = new CoopRoom(id, bossEventId == null ? "" : bossEventId.trim(), leaderId,
                RoomStatus.OPEN, List.of(new Member(leaderId, now)), hp, hp, now);
        rooms.put(id, room);
        playerRoom.put(leaderId, id);
        roomDamage.put(id, new ConcurrentHashMap<>());
        spectators.put(id, new CopyOnWriteArrayList<>());
        feed.put(id, new CopyOnWriteArrayList<>());
        likes.put(id, new ConcurrentHashMap<>());
        flowers.put(id, new ConcurrentHashMap<>());
        return room;
    }

    public synchronized CoopRoom join(long playerId, String roomId) {
        CoopRoom room = require(roomId);
        if (room.status() == RoomStatus.CLOSED || room.status() == RoomStatus.IN_BOSS) {
            throw new IllegalStateException("room_not_joinable");
        }
        if (room.members().size() >= MAX_MEMBERS) {
            throw new IllegalStateException("room_full");
        }
        leaveIfAny(playerId);
        leaveSpectateIfAny(playerId);
        List<Member> members = new ArrayList<>(room.members());
        members.add(new Member(playerId, System.currentTimeMillis()));
        RoomStatus status = members.size() >= MAX_MEMBERS ? RoomStatus.FULL : RoomStatus.OPEN;
        CoopRoom updated = new CoopRoom(room.roomId(), room.bossEventId(), room.leaderId(),
                status, List.copyOf(members), room.bossHp(), room.bossHpMax(), room.createdAtMs());
        rooms.put(roomId, updated);
        playerRoom.put(playerId, roomId);
        return updated;
    }

    public synchronized CoopRoom leave(long playerId) {
        String rid = playerRoom.remove(playerId);
        if (rid == null) {
            return null;
        }
        CoopRoom room = rooms.get(rid);
        if (room == null) {
            return null;
        }
        List<Member> members = new ArrayList<>();
        for (Member m : room.members()) {
            if (m.playerId() != playerId) {
                members.add(m);
            }
        }
        if (members.isEmpty()) {
            CoopRoom closed = new CoopRoom(room.roomId(), room.bossEventId(), room.leaderId(),
                    RoomStatus.CLOSED, List.of(), room.bossHp(), room.bossHpMax(), room.createdAtMs());
            rooms.put(rid, closed);
            return closed;
        }
        long leader = room.leaderId() == playerId ? members.get(0).playerId() : room.leaderId();
        CoopRoom updated = new CoopRoom(room.roomId(), room.bossEventId(), leader,
                RoomStatus.OPEN, List.copyOf(members), room.bossHp(), room.bossHpMax(), room.createdAtMs());
        rooms.put(rid, updated);
        return updated;
    }

    public synchronized CoopRoom startBoss(String roomId, long requesterId) {
        CoopRoom room = require(roomId);
        if (room.leaderId() != requesterId) {
            throw new IllegalStateException("not_leader");
        }
        if (room.members().isEmpty()) {
            throw new IllegalStateException("empty_room");
        }
        CoopRoom updated = new CoopRoom(room.roomId(), room.bossEventId(), room.leaderId(),
                RoomStatus.IN_BOSS, room.members(), room.bossHp(), room.bossHpMax(), room.createdAtMs());
        rooms.put(roomId, updated);
        return updated;
    }

    public synchronized CoopRoom reportDamage(String roomId, long playerId, long damage) {
        CoopRoom room = require(roomId);
        if (room.status() != RoomStatus.IN_BOSS) {
            throw new IllegalStateException("boss_not_started");
        }
        if (!isMember(room, playerId) || damage <= 0) {
            throw new IllegalArgumentException("invalid_damage");
        }
        roomDamage.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, id -> new AtomicLong())
                .addAndGet(damage);
        long newHp = Math.max(0L, room.bossHp() - damage);
        RoomStatus status = newHp <= 0 ? RoomStatus.CLOSED : RoomStatus.IN_BOSS;
        CoopRoom updated = new CoopRoom(room.roomId(), room.bossEventId(), room.leaderId(),
                status, room.members(), newHp, room.bossHpMax(), room.createdAtMs());
        rooms.put(roomId, updated);
        return updated;
    }

    public Map<String, Object> sendEmote(String roomId, long playerId, String emoteCode) {
        return pushFeed(roomId, playerId, 0L, "EMOTE", emoteCode == null ? "wave" : emoteCode.trim());
    }

    public Map<String, Object> sendAction(String roomId, long playerId, String actionCode) {
        return pushFeed(roomId, playerId, 0L, "ACTION", actionCode == null ? "cheer" : actionCode.trim());
    }

    public Map<String, Object> sendQuickPhrase(String roomId, long playerId, String phraseId) {
        return pushFeed(roomId, playerId, 0L, "PHRASE", phraseId == null ? "nice" : phraseId.trim());
    }

    public Map<String, Object> spectate(long spectatorId, String roomId) {
        if (spectatorId <= 0) {
            return Map.of("ok", false, "error", "invalid_spectator");
        }
        CoopRoom room = require(roomId);
        if (room.status() == RoomStatus.CLOSED) {
            return Map.of("ok", false, "error", "room_closed");
        }
        if (isMember(room, spectatorId)) {
            return Map.of("ok", false, "error", "already_member");
        }
        leaveSpectateIfAny(spectatorId);
        leaveIfAny(spectatorId);
        CopyOnWriteArrayList<Long> list = spectators.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        if (list.size() >= MAX_SPECTATORS) {
            return Map.of("ok", false, "error", "spectator_full");
        }
        if (!list.contains(spectatorId)) {
            list.add(spectatorId);
        }
        spectatorRoom.put(spectatorId, roomId);
        socialEventPublisher.publishCoopInteraction(roomId, "SPECTATE", spectatorId, room.leaderId());
        Map<String, Object> out = toView(room);
        out.put("role", "spectator");
        return out;
    }

    public Map<String, Object> leaveSpectate(long spectatorId) {
        String rid = spectatorRoom.remove(spectatorId);
        if (rid == null) {
            return Map.of("ok", true, "left", false);
        }
        CopyOnWriteArrayList<Long> list = spectators.get(rid);
        if (list != null) {
            list.remove(spectatorId);
        }
        return Map.of("ok", true, "left", true, "roomId", rid);
    }

    public Map<String, Object> like(String roomId, long fromId, long toId) {
        CoopRoom room = require(roomId);
        if (!canInteract(room, fromId)) {
            return Map.of("ok", false, "error", "not_in_room");
        }
        if (!isMember(room, toId)) {
            return Map.of("ok", false, "error", "target_not_member");
        }
        long total = likes.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(toId, id -> new AtomicLong())
                .incrementAndGet();
        pushFeed(roomId, fromId, toId, "LIKE", "1");
        socialEventPublisher.publishCoopInteraction(roomId, "LIKE", fromId, toId);
        return Map.of("ok", true, "toId", toId, "likes", total);
    }

    public Map<String, Object> sendFlower(String roomId, long fromId, long toId, int count) {
        CoopRoom room = require(roomId);
        if (!canInteract(room, fromId)) {
            return Map.of("ok", false, "error", "not_in_room");
        }
        if (!isMember(room, toId)) {
            return Map.of("ok", false, "error", "target_not_member");
        }
        int n = Math.max(1, Math.min(99, count));
        long total = flowers.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(toId, id -> new AtomicLong())
                .addAndGet(n);
        pushFeed(roomId, fromId, toId, "FLOWER", String.valueOf(n));
        socialEventPublisher.publishCoopInteraction(roomId, "FLOWER", fromId, toId);
        return Map.of("ok", true, "toId", toId, "flowers", total, "sent", n);
    }

    public CoopRoom get(String roomId) {
        return rooms.get(roomId);
    }

    public CoopRoom currentOf(long playerId) {
        String rid = playerRoom.get(playerId);
        return rid == null ? null : rooms.get(rid);
    }

    public Map<String, Object> damageBoard(String roomId) {
        ConcurrentHashMap<Long, AtomicLong> board = roomDamage.getOrDefault(roomId, new ConcurrentHashMap<>());
        Map<String, Object> out = new LinkedHashMap<>();
        board.forEach((pid, dmg) -> out.put(String.valueOf(pid), dmg.get()));
        return out;
    }

    public Map<String, Object> toView(CoopRoom room) {
        if (room == null) {
            return Map.of("ok", false, "error", "not_found");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("roomId", room.roomId());
        m.put("bossEventId", room.bossEventId());
        m.put("leaderId", room.leaderId());
        m.put("status", room.status().name());
        m.put("bossHp", room.bossHp());
        m.put("bossHpMax", room.bossHpMax());
        m.put("maxMembers", MAX_MEMBERS);
        m.put("members", room.members().stream().map(mem -> Map.of(
                "playerId", mem.playerId(), "joinedAtMs", mem.joinedAtMs())).toList());
        m.put("damageBoard", damageBoard(room.roomId()));
        List<Long> specs = spectators.getOrDefault(room.roomId(), new CopyOnWriteArrayList<>());
        m.put("spectators", List.copyOf(specs));
        m.put("spectatorCount", specs.size());
        m.put("likes", toLongMap(likes.get(room.roomId())));
        m.put("flowers", toLongMap(flowers.get(room.roomId())));
        List<Map<String, Object>> f = feed.getOrDefault(room.roomId(), new CopyOnWriteArrayList<>());
        m.put("feed", f.size() > 20 ? f.subList(0, 20) : List.copyOf(f));
        return m;
    }

    private Map<String, Object> pushFeed(String roomId, long actorId, long targetId, String type, String value) {
        CoopRoom room = require(roomId);
        if (!canInteract(room, actorId)) {
            return Map.of("ok", false, "error", "not_in_room");
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("actorId", actorId);
        entry.put("targetId", targetId);
        entry.put("value", value);
        entry.put("ts", System.currentTimeMillis());
        CopyOnWriteArrayList<Map<String, Object>> list = feed.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        list.add(0, entry);
        while (list.size() > MAX_FEED) {
            list.remove(list.size() - 1);
        }
        socialEventPublisher.publishCoopInteraction(roomId, type, actorId, targetId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("entry", entry);
        return out;
    }

    private boolean canInteract(CoopRoom room, long playerId) {
        return isMember(room, playerId) || spectatorRoom.getOrDefault(playerId, "").equals(room.roomId());
    }

    private void leaveIfAny(long playerId) {
        if (playerRoom.containsKey(playerId)) {
            leave(playerId);
        }
    }

    private void leaveSpectateIfAny(long playerId) {
        if (spectatorRoom.containsKey(playerId)) {
            leaveSpectate(playerId);
        }
    }

    private CoopRoom require(String roomId) {
        CoopRoom room = rooms.get(roomId);
        if (room == null || room.status() == RoomStatus.CLOSED && room.members().isEmpty()) {
            throw new IllegalStateException("room_not_found");
        }
        return room;
    }

    private static boolean isMember(CoopRoom room, long playerId) {
        for (Member m : room.members()) {
            if (m.playerId() == playerId) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Long> toLongMap(ConcurrentHashMap<Long, AtomicLong> src) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (src != null) {
            src.forEach((k, v) -> out.put(String.valueOf(k), v.get()));
        }
        return out;
    }
}
