package cn.itcast.demo.mymmorpg.world.explore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域觉醒：探索度 100% → REGION_MASTERY 全服广播 + 72h 全属性 WorldBuff（最多 3 层）。
 */
@Service
public class RegionAwakeningService {

    public static final String EVENT_REGION_MASTERY = "REGION_MASTERY";
    public static final String EVENT_EPIC_MVP = "EPIC_MVP";
    public static final long BUFF_DURATION_MS = 72L * 3_600_000L;
    public static final int MAX_REGION_BUFFS = 3;
    public static final double ATTR_BONUS = 0.05;

    public record WorldBuff(
            String regionId,
            long expiresAtMs,
            double allAttrBonus) {
    }

    private final RegionProgressService progress;
    private final ConcurrentHashMap<Long, List<WorldBuff>> buffs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> mastered = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Map<String, Object>>> regionBroadcasts =
            new ConcurrentHashMap<>();

    public RegionAwakeningService() {
        this(new RegionProgressService());
    }

    public RegionAwakeningService(RegionProgressService progress) {
        this.progress = progress == null ? new RegionProgressService() : progress;
    }

    public RegionProgressService progress() {
        return progress;
    }

    /**
     * 监测探索度；达 100% 触发觉醒。
     */
    public Map<String, Object> checkAndAwaken(
            long playerId, String playerName, String regionId, long nowMs) {
        Map<String, Object> status = progress.status(playerId, regionId);
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            return status;
        }
        Object rp = status.get("region_progress");
        if (rp == null) {
            rp = status.get("regionProgress");
        }
        int percent = 0;
        if (rp instanceof Map<?, ?> m && m.get("percent") instanceof Number n) {
            percent = n.intValue();
        } else if (status.get("percent") instanceof Number n) {
            percent = n.intValue();
        }
        if (percent < 100) {
            return Map.of("ok", true, "awakened", false, "percent", percent, "regionId", regionId);
        }
        String key = playerId + ":" + regionId;
        if (Boolean.TRUE.equals(mastered.putIfAbsent(key, true))) {
            return Map.of("ok", true, "awakened", false, "alreadyMastered", true,
                    "regionId", regionId, "activeBuffs", activeBuffs(playerId, nowMs));
        }

        List<WorldBuff> list = new ArrayList<>(buffs.getOrDefault(playerId, List.of()));
        list.removeIf(b -> b.expiresAtMs() <= nowMs);
        list.removeIf(b -> b.regionId().equals(regionId));
        while (list.size() >= MAX_REGION_BUFFS) {
            list.remove(0);
        }
        WorldBuff buff = new WorldBuff(regionId, nowMs + BUFF_DURATION_MS, ATTR_BONUS);
        list.add(buff);
        buffs.put(playerId, list);

        Map<String, Object> epic = new LinkedHashMap<>();
        epic.put("event", EVENT_EPIC_MVP);
        epic.put("playerId", playerId);
        epic.put("playerName", playerName == null ? ("p" + playerId) : playerName);
        epic.put("regionId", regionId);
        epic.put("mastery", EVENT_REGION_MASTERY);
        epic.put("atMs", nowMs);
        regionBroadcasts.computeIfAbsent(regionId, id -> new ArrayList<>()).add(epic);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("awakened", true);
        body.put("event", EVENT_REGION_MASTERY);
        body.put("broadcast", epic);
        body.put("worldBuff", Map.of(
                "regionId", regionId,
                "allAttrBonus", ATTR_BONUS,
                "durationMs", BUFF_DURATION_MS,
                "expiresAtMs", buff.expiresAtMs(),
                "stackCount", list.size(),
                "maxStacks", MAX_REGION_BUFFS));
        body.put("activeBuffs", activeBuffs(playerId, nowMs));
        return body;
    }

    public List<Map<String, Object>> activeBuffs(long playerId, long nowMs) {
        List<WorldBuff> list = buffs.getOrDefault(playerId, List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        double total = 0;
        for (WorldBuff b : list) {
            if (b.expiresAtMs() <= nowMs) {
                continue;
            }
            total += b.allAttrBonus();
            out.add(Map.of(
                    "regionId", b.regionId(),
                    "allAttrBonus", b.allAttrBonus(),
                    "expiresAtMs", b.expiresAtMs(),
                    "remainMs", b.expiresAtMs() - nowMs));
        }
        if (!out.isEmpty()) {
            // 附带合计便于客户端展示
            out = new ArrayList<>(out);
        }
        return out;
    }

    public double totalAttrBonus(long playerId, long nowMs) {
        return activeBuffs(playerId, nowMs).stream()
                .mapToDouble(m -> m.get("allAttrBonus") instanceof Number n ? n.doubleValue() : 0)
                .sum();
    }

    public List<Map<String, Object>> broadcasts(String regionId) {
        return List.copyOf(regionBroadcasts.getOrDefault(regionId, List.of()));
    }
}
