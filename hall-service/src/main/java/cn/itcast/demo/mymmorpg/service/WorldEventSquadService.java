package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩法驱动的轻社交：围绕世界事件/高难挑战的临时小队（非公会绑定）。
 */
@Service
public class WorldEventSquadService {

    public enum SquadStatus {
        OPEN, FULL, DISBANDED, IN_EVENT
    }

    public record SquadMember(long playerId, long joinedAtMs) {
    }

    public record Squad(
            String squadId,
            String eventId,
            long leaderId,
            int maxSize,
            SquadStatus status,
            List<SquadMember> members,
            long createdAtMs) {
    }

    private final ConcurrentHashMap<String, Squad> squads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerSquad = new ConcurrentHashMap<>();

    public Squad create(long leaderId, String eventId, int maxSize) {
        if (leaderId <= 0 || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("invalid leader or eventId");
        }
        leaveIfAny(leaderId);
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int cap = Math.max(2, Math.min(maxSize <= 0 ? 4 : maxSize, 8));
        long now = System.currentTimeMillis();
        Squad squad = new Squad(id, eventId.trim(), leaderId, cap, SquadStatus.OPEN,
                List.of(new SquadMember(leaderId, now)), now);
        squads.put(id, squad);
        playerSquad.put(leaderId, id);
        return squad;
    }

    public synchronized Squad join(long playerId, String squadId) {
        if (playerId <= 0 || squadId == null) {
            throw new IllegalArgumentException("invalid join");
        }
        Squad squad = squads.get(squadId);
        if (squad == null || squad.status() == SquadStatus.DISBANDED) {
            throw new IllegalStateException("squad_not_found");
        }
        if (squad.status() == SquadStatus.FULL || squad.members().size() >= squad.maxSize()) {
            throw new IllegalStateException("squad_full");
        }
        leaveIfAny(playerId);
        List<SquadMember> members = new ArrayList<>(squad.members());
        members.add(new SquadMember(playerId, System.currentTimeMillis()));
        SquadStatus status = members.size() >= squad.maxSize() ? SquadStatus.FULL : SquadStatus.OPEN;
        Squad updated = new Squad(squad.squadId(), squad.eventId(), squad.leaderId(),
                squad.maxSize(), status, List.copyOf(members), squad.createdAtMs());
        squads.put(squadId, updated);
        playerSquad.put(playerId, squadId);
        return updated;
    }

    public synchronized Squad leave(long playerId) {
        String sid = playerSquad.remove(playerId);
        if (sid == null) {
            return null;
        }
        Squad squad = squads.get(sid);
        if (squad == null) {
            return null;
        }
        List<SquadMember> members = new ArrayList<>();
        for (SquadMember m : squad.members()) {
            if (m.playerId() != playerId) {
                members.add(m);
            }
        }
        if (members.isEmpty()) {
            Squad disbanded = new Squad(squad.squadId(), squad.eventId(), squad.leaderId(),
                    squad.maxSize(), SquadStatus.DISBANDED, List.of(), squad.createdAtMs());
            squads.put(sid, disbanded);
            return disbanded;
        }
        long leader = squad.leaderId() == playerId ? members.get(0).playerId() : squad.leaderId();
        Squad updated = new Squad(squad.squadId(), squad.eventId(), leader, squad.maxSize(),
                SquadStatus.OPEN, List.copyOf(members), squad.createdAtMs());
        squads.put(sid, updated);
        return updated;
    }

    public Squad startEvent(String squadId) {
        Squad squad = squads.get(squadId);
        if (squad == null || squad.members().isEmpty()) {
            throw new IllegalStateException("squad_not_found");
        }
        Squad updated = new Squad(squad.squadId(), squad.eventId(), squad.leaderId(),
                squad.maxSize(), SquadStatus.IN_EVENT, squad.members(), squad.createdAtMs());
        squads.put(squadId, updated);
        return updated;
    }

    public Squad get(String squadId) {
        return squads.get(squadId);
    }

    public Squad currentOf(long playerId) {
        String sid = playerSquad.get(playerId);
        return sid == null ? null : squads.get(sid);
    }

    public List<Squad> listOpenByEvent(String eventId) {
        List<Squad> out = new ArrayList<>();
        for (Squad s : squads.values()) {
            if (s.status() == SquadStatus.OPEN && s.eventId().equals(eventId)) {
                out.add(s);
            }
        }
        return out;
    }

    public Map<String, Object> toView(Squad s) {
        if (s == null) {
            return Map.of("ok", false);
        }
        List<Long> memberIds = s.members().stream().map(SquadMember::playerId).toList();
        return Map.of(
                "ok", true,
                "squadId", s.squadId(),
                "eventId", s.eventId(),
                "leaderId", s.leaderId(),
                "maxSize", s.maxSize(),
                "status", s.status().name(),
                "memberIds", memberIds,
                "memberCount", memberIds.size());
    }

    private void leaveIfAny(long playerId) {
        if (playerSquad.containsKey(playerId)) {
            leave(playerId);
        }
    }
}
