package cn.itcast.demo.mymmorpg.world.endgame;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 野外协同攻城：最多 20 人同 Zone，Boss 部位破坏 + 元素法阵破盾。
 */
@Service
public class SiegeWarService {

    public static final int MAX_PLAYERS = 20;
    public static final String PART_BREAK = "PART_BREAK";

    public enum BossPart {
        LEFT_LEG, RIGHT_ARM, CORE
    }

    private final ConcurrentHashMap<String, List<Long>> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<BossPart, Boolean>> partsBroken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> scores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> invincible = new ConcurrentHashMap<>();
    /** roomId → part → (bonus, expiresAtMs) */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, long[]>> focusFire =
            new ConcurrentHashMap<>();

    public Map<String, Object> createSiegeRoom(String roomId, long leaderId) {
        rooms.put(roomId, new ArrayList<>(List.of(leaderId)));
        Map<BossPart, Boolean> parts = new ConcurrentHashMap<>();
        for (BossPart p : BossPart.values()) {
            parts.put(p, false);
        }
        partsBroken.put(roomId, parts);
        invincible.put(roomId, true);
        scores.put(roomId, new AtomicInteger(0));
        return Map.of("ok", true, "roomId", roomId, "type", "SiegeRoom",
                "maxPlayers", MAX_PLAYERS, "leaderId", leaderId);
    }

    public Map<String, Object> join(String roomId, long playerId) {
        List<Long> members = rooms.get(roomId);
        if (members == null) {
            return Map.of("ok", false, "error", "room_not_found");
        }
        synchronized (members) {
            if (members.size() >= MAX_PLAYERS) {
                return Map.of("ok", false, "error", "room_full");
            }
            if (!members.contains(playerId)) {
                members.add(playerId);
            }
            return Map.of("ok", true, "roomId", roomId, "size", members.size(),
                    "maxPlayers", MAX_PLAYERS);
        }
    }

    public Map<String, Object> breakPart(String roomId, BossPart part) {
        return breakPart(roomId, part, 100, System.currentTimeMillis());
    }

    public Map<String, Object> breakPart(String roomId, BossPart part, int baseDamage, long nowMs) {
        Map<BossPart, Boolean> parts = partsBroken.get(roomId);
        if (parts == null) {
            return Map.of("ok", false, "error", "room_not_found");
        }
        double mul = focusDamageMul(roomId, part == null ? "" : part.name(), nowMs);
        int effective = (int) Math.round(Math.max(0, baseDamage) * mul);
        parts.put(part, true);
        scores.computeIfAbsent(roomId, k -> new AtomicInteger(0)).addAndGet(50 + (mul > 1.0 ? 15 : 0));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", PART_BREAK);
        body.put("part", part.name());
        body.put("baseDamage", baseDamage);
        body.put("damageMul", mul);
        body.put("effectiveDamage", effective);
        if (part == BossPart.LEFT_LEG) {
            body.put("moveSpeedMul", 0.5);
        } else if (part == BossPart.RIGHT_ARM) {
            body.put("damageMulBoss", 0.7);
        } else if (part == BossPart.CORE) {
            body.put("coreExposed", true);
        }
        return body;
    }

    /** 指挥集火：部位伤害临时 +15%，持续至 expiresAtMs。 */
    public Map<String, Object> armFocusFire(
            String roomId, String partId, double bonus, long expiresAtMs) {
        if (!rooms.containsKey(roomId)) {
            return Map.of("ok", false, "error", "room_not_found");
        }
        String part = partId == null ? "CORE" : partId.trim();
        focusFire.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                .put(part, new long[]{Double.doubleToLongBits(bonus), expiresAtMs});
        return Map.of("ok", true, "roomId", roomId, "part", part,
                "damageBonus", bonus, "expiresAtMs", expiresAtMs);
    }

    public double focusDamageMul(String roomId, String partId, long nowMs) {
        ConcurrentHashMap<String, long[]> map = focusFire.get(roomId);
        if (map == null) {
            return 1.0;
        }
        long[] row = map.get(partId == null ? "" : partId.trim());
        if (row == null || row[1] <= nowMs) {
            return 1.0;
        }
        return 1.0 + Double.longBitsToDouble(row[0]);
    }

    /**
     * 核心暴露阶段：4 人站不同元素法阵破除无敌盾。
     */
    public Map<String, Object> breakInvincibleShield(
            String roomId, List<String> standingElements) {
        if (!Boolean.TRUE.equals(invincible.get(roomId))) {
            return Map.of("ok", false, "error", "not_invincible");
        }
        Map<BossPart, Boolean> parts = partsBroken.get(roomId);
        if (parts == null || !Boolean.TRUE.equals(parts.get(BossPart.CORE))) {
            return Map.of("ok", false, "error", "core_not_exposed");
        }
        if (standingElements == null || standingElements.stream().distinct().count() < 4) {
            return Map.of("ok", false, "error", "need_4_distinct_elements");
        }
        invincible.put(roomId, false);
        scores.computeIfAbsent(roomId, k -> new AtomicInteger(0)).addAndGet(200);
        return Map.of("ok", true, "invincibleBroken", true, "elements", standingElements);
    }

    public Map<String, Object> settle(String roomId, int percentileScore) {
        int partBonus = scores.getOrDefault(roomId, new AtomicInteger(0)).get();
        int total = percentileScore + partBonus;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("roomId", roomId);
        body.put("percentileScore", percentileScore);
        body.put("partBreakBonus", partBonus);
        body.put("totalScore", total);
        body.put("rewards", List.of(
                Map.of("itemId", "siege_coin", "count", Math.max(1, total / 10)),
                Map.of("itemId", "siege_skin_unique", "count", 1)));
        body.put("grantPlans", body.get("rewards"));
        body.put("idempotencyKey", "siege:" + roomId);
        return body;
    }
}
