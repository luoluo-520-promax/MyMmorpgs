package cn.itcast.demo.mymmorpg.world.ai;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.world.endgame.SiegeWarService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集群指挥 AI：队长指令覆盖队员个体 BT（射箭齐射 / 盾墙）+ 战斗即时标记/集火。
 */
@Service
public class SquadCommanderService {

    public static final String CMD_ARCHERY_VOLLEY = "COMMAND_ARCHERY_VOLLEY";
    public static final String CMD_SHIELD_WALL = "COMMAND_SHIELD_WALL";
    public static final String CMD_QUICK_MARK = "QUICK_MARK";
    public static final String CMD_FOCUS_FIRE = "FOCUS_FIRE";
    public static final String STATE_BERSERK = "BERSERK";
    public static final double FOCUS_DAMAGE_BONUS = 0.15;
    public static final long FOCUS_DURATION_MS = 5_000L;

    public record SquadTemplate(String squadId, long leaderId, List<Long> memberIds) {
        public SquadTemplate {
            squadId = squadId == null ? "" : squadId.trim();
            memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
        }
    }

    public record QuickMark(
            String squadId,
            long leaderId,
            float x, float y, float z,
            String bossPartId,
            boolean focusFire,
            long expiresAtMs) {
    }

    private final ConcurrentHashMap<String, SquadTemplate> squads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeCommand = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> memberOverride = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> berserk = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QuickMark> marks = new ConcurrentHashMap<>();
    private SiegeWarService siegeWar;

    public void register(SquadTemplate template) {
        if (template != null && !template.squadId().isBlank()) {
            squads.put(template.squadId(), template);
        }
    }

    public void bindSiegeWar(SiegeWarService siegeWar) {
        this.siegeWar = siegeWar;
    }

    /**
     * 队长长按锁定目标：全队 AOI 广播 MARK_TARGET；可选集火使部位伤害 +15%/5s。
     * MsgId {@link MessageId#QUICK_MARK_CS_REQ} = 2230。
     */
    public Map<String, Object> quickMark(
            String squadId, long leaderId,
            float x, float y, float z,
            String bossPartId, boolean focusFire,
            String siegeRoomId, long nowMs) {
        SquadTemplate s = squads.get(squadId == null ? "" : squadId.trim());
        if (s == null) {
            return Map.of("ok", false, "error", "squad_not_found");
        }
        if (s.leaderId() != leaderId) {
            return Map.of("ok", false, "error", "not_squad_leader");
        }
        String part = bossPartId == null || bossPartId.isBlank() ? "CORE" : bossPartId.trim();
        long expire = nowMs + FOCUS_DURATION_MS;
        QuickMark mark = new QuickMark(s.squadId(), leaderId, x, y, z, part, focusFire, expire);
        marks.put(s.squadId(), mark);
        activeCommand.put(s.squadId(), focusFire ? CMD_FOCUS_FIRE : CMD_QUICK_MARK);

        Map<String, Object> broadcast = new LinkedHashMap<>();
        broadcast.put("event", "MARK_TARGET");
        broadcast.put("msgId", MessageId.MARK_TARGET_SC_NOTIFY);
        broadcast.put("squadId", s.squadId());
        broadcast.put("leaderId", leaderId);
        broadcast.put("x", x);
        broadcast.put("y", y);
        broadcast.put("z", z);
        broadcast.put("bossPartId", part);
        broadcast.put("focusFire", focusFire);
        broadcast.put("partyMemberIds", s.memberIds());
        broadcast.put("aoiBroadcast", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("command", focusFire ? CMD_FOCUS_FIRE : CMD_QUICK_MARK);
        body.put("broadcast", broadcast);
        if (focusFire) {
            body.put("partDamageBonus", FOCUS_DAMAGE_BONUS);
            body.put("bonusDurationMs", FOCUS_DURATION_MS);
            body.put("expiresAtMs", expire);
            if (siegeWar != null && siegeRoomId != null && !siegeRoomId.isBlank()) {
                body.put("siegeFocus", siegeWar.armFocusFire(siegeRoomId, part, FOCUS_DAMAGE_BONUS, expire));
            }
        }
        return body;
    }

    public QuickMark markOf(String squadId) {
        return marks.get(squadId == null ? "" : squadId.trim());
    }

    /** 集火窗口内对标记部位的伤害倍率。 */
    public double focusDamageMul(String squadId, String bossPartId, long nowMs) {
        QuickMark m = markOf(squadId);
        if (m == null || !m.focusFire() || m.expiresAtMs() <= nowMs) {
            return 1.0;
        }
        if (bossPartId != null && !bossPartId.equalsIgnoreCase(m.bossPartId())) {
            return 1.0;
        }
        return 1.0 + FOCUS_DAMAGE_BONUS;
    }

    public Map<String, Object> issueCommand(String squadId, String command) {
        SquadTemplate s = squads.get(squadId == null ? "" : squadId.trim());
        if (s == null) {
            return Map.of("ok", false, "error", "squad_not_found");
        }
        activeCommand.put(s.squadId(), command);
        for (Long mid : s.memberIds()) {
            memberOverride.put(mid, command);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("squadId", s.squadId());
        body.put("command", command);
        body.put("priorityOverIndividualBt", true);
        if (CMD_SHIELD_WALL.equals(command)) {
            body.put("blockRateBonus", 0.6);
            body.put("formation", "front_of_leader");
        }
        if (CMD_ARCHERY_VOLLEY.equals(command)) {
            body.put("syncFire", true);
            body.put("members", s.memberIds());
        }
        return body;
    }

    public Map<String, Object> onLeaderDeath(String squadId) {
        SquadTemplate s = squads.get(squadId == null ? "" : squadId.trim());
        if (s == null) {
            return Map.of("ok", false, "error", "squad_not_found");
        }
        activeCommand.remove(s.squadId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long mid : s.memberIds()) {
            memberOverride.remove(mid);
            berserk.put(mid, true);
            rows.add(Map.of("memberId", mid, "state", STATE_BERSERK,
                    "atkMul", 1.3, "defMul", 0.5));
        }
        return Map.of("ok", true, "squadId", s.squadId(), "leaderDead", true, "members", rows);
    }

    public String overrideOf(long memberId) {
        return memberOverride.get(memberId);
    }

    public boolean isBerserk(long memberId) {
        return Boolean.TRUE.equals(berserk.get(memberId));
    }
}
