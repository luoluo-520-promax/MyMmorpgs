package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 四人联机队伍管理：创建/邀请码加入/踢人/队长转移/解散。
 */
@Service
public class PartyManageService {

    public static final int MAX_MEMBERS = 4;

    public record Party(
            String partyId,
            String inviteCode,
            long leaderId,
            List<Long> memberIds,
            long createdAtMs) {
    }

    private final ConcurrentHashMap<String, Party> parties = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> playerParty = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> inviteIndex = new ConcurrentHashMap<>();

    public Map<String, Object> create(long leaderId) {
        if (leaderId <= 0) {
            return Map.of("ok", false, "error", "invalid_leader");
        }
        if (playerParty.containsKey(leaderId)) {
            return Map.of("ok", false, "error", "already_in_party");
        }
        String partyId = "pty-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String invite = genInviteCode();
        Party party = new Party(partyId, invite, leaderId, List.of(leaderId), System.currentTimeMillis());
        parties.put(partyId, party);
        playerParty.put(leaderId, partyId);
        inviteIndex.put(invite, partyId);
        return toView(party);
    }

    public Map<String, Object> joinByInvite(long playerId, String inviteCode) {
        if (playerId <= 0 || inviteCode == null || inviteCode.isBlank()) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        if (playerParty.containsKey(playerId)) {
            return Map.of("ok", false, "error", "already_in_party");
        }
        String partyId = inviteIndex.get(inviteCode.trim().toUpperCase());
        if (partyId == null) {
            return Map.of("ok", false, "error", "invite_not_found");
        }
        Party cur = parties.get(partyId);
        if (cur == null) {
            return Map.of("ok", false, "error", "party_not_found");
        }
        if (cur.memberIds().size() >= MAX_MEMBERS) {
            return Map.of("ok", false, "error", "party_full");
        }
        List<Long> members = new ArrayList<>(cur.memberIds());
        members.add(playerId);
        Party next = new Party(cur.partyId(), cur.inviteCode(), cur.leaderId(), List.copyOf(members), cur.createdAtMs());
        parties.put(partyId, next);
        playerParty.put(playerId, partyId);
        return toView(next);
    }

    public Map<String, Object> kick(long operatorId, long targetId) {
        Party cur = currentParty(operatorId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_in_party");
        }
        if (cur.leaderId() != operatorId) {
            return Map.of("ok", false, "error", "not_leader");
        }
        if (targetId == operatorId) {
            return Map.of("ok", false, "error", "cannot_kick_self");
        }
        if (!cur.memberIds().contains(targetId)) {
            return Map.of("ok", false, "error", "target_not_member");
        }
        List<Long> members = new ArrayList<>(cur.memberIds());
        members.remove(targetId);
        playerParty.remove(targetId);
        Party next = new Party(cur.partyId(), cur.inviteCode(), cur.leaderId(), List.copyOf(members), cur.createdAtMs());
        parties.put(cur.partyId(), next);
        return toView(next);
    }

    public Map<String, Object> transferLeader(long operatorId, long newLeaderId) {
        Party cur = currentParty(operatorId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_in_party");
        }
        if (cur.leaderId() != operatorId) {
            return Map.of("ok", false, "error", "not_leader");
        }
        if (!cur.memberIds().contains(newLeaderId)) {
            return Map.of("ok", false, "error", "target_not_member");
        }
        Party next = new Party(cur.partyId(), cur.inviteCode(), newLeaderId, cur.memberIds(), cur.createdAtMs());
        parties.put(cur.partyId(), next);
        return toView(next);
    }

    public Map<String, Object> leave(long playerId) {
        Party cur = currentParty(playerId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_in_party");
        }
        List<Long> members = new ArrayList<>(cur.memberIds());
        members.remove(playerId);
        playerParty.remove(playerId);
        if (members.isEmpty()) {
            parties.remove(cur.partyId());
            inviteIndex.remove(cur.inviteCode());
            return Map.of("ok", true, "disbanded", true, "partyId", cur.partyId());
        }
        long leader = cur.leaderId() == playerId ? members.get(0) : cur.leaderId();
        Party next = new Party(cur.partyId(), cur.inviteCode(), leader, List.copyOf(members), cur.createdAtMs());
        parties.put(cur.partyId(), next);
        return toView(next);
    }

    public Map<String, Object> mine(long playerId) {
        Party cur = currentParty(playerId);
        return cur == null ? Map.of("ok", true, "party", null) : toView(cur);
    }

    public Party currentParty(long playerId) {
        String id = playerParty.get(playerId);
        return id == null ? null : parties.get(id);
    }

    private static String genInviteCode() {
        int n = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "P" + n;
    }

    public static Map<String, Object> toView(Party party) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("partyId", party.partyId());
        m.put("inviteCode", party.inviteCode());
        m.put("leaderId", party.leaderId());
        m.put("memberIds", party.memberIds());
        m.put("memberCount", party.memberIds().size());
        m.put("maxMembers", MAX_MEMBERS);
        m.put("createdAtMs", party.createdAtMs());
        return m;
    }
}
