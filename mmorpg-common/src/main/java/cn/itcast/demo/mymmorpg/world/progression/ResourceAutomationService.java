package cn.itcast.demo.mymmorpg.world.progression;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资源自动化：通过建设/投资实现基础资源自动收集，减轻重复劳动。
 */
@Service
public class ResourceAutomationService {

    public record FacilityDef(
            String facilityId,
            String label,
            String outputItemId,
            int outputPerHour,
            int buildCostGold,
            int maxLevel) {

        public FacilityDef {
            outputPerHour = Math.max(1, outputPerHour);
            maxLevel = Math.max(1, maxLevel);
        }
    }

    public record PlayerFacility(
            String facilityId,
            int level,
            long lastClaimMs,
            long builtAtMs) {
    }

    private final ConcurrentHashMap<String, FacilityDef> defs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, PlayerFacility>> playerFacilities =
            new ConcurrentHashMap<>();

    public void register(FacilityDef def) {
        if (def != null && def.facilityId() != null) {
            defs.put(def.facilityId(), def);
        }
    }

    public Map<String, Object> build(long playerId, String facilityId, long nowMs) {
        FacilityDef def = defs.get(facilityId);
        if (def == null) {
            return Map.of("ok", false, "error", "facility_not_found");
        }
        ConcurrentHashMap<String, PlayerFacility> owned =
                playerFacilities.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        if (owned.containsKey(facilityId)) {
            return upgrade(playerId, facilityId, nowMs);
        }
        owned.put(facilityId, new PlayerFacility(facilityId, 1, nowMs, nowMs));
        return Map.of("ok", true, "facilityId", facilityId, "level", 1,
                "buildCostGold", def.buildCostGold(), "outputPerHour", def.outputPerHour());
    }

    public Map<String, Object> upgrade(long playerId, String facilityId, long nowMs) {
        FacilityDef def = defs.get(facilityId);
        PlayerFacility pf = playerFacilities
                .getOrDefault(playerId, new ConcurrentHashMap<>())
                .get(facilityId);
        if (def == null || pf == null) {
            return Map.of("ok", false, "error", "not_built");
        }
        if (pf.level() >= def.maxLevel()) {
            return Map.of("ok", false, "error", "max_level");
        }
        int next = pf.level() + 1;
        playerFacilities.get(playerId).put(facilityId,
                new PlayerFacility(facilityId, next, pf.lastClaimMs(), pf.builtAtMs()));
        return Map.of("ok", true, "facilityId", facilityId, "level", next,
                "outputPerHour", def.outputPerHour() * next);
    }

    public Map<String, Object> claimAll(long playerId, long nowMs) {
        ConcurrentHashMap<String, PlayerFacility> owned =
                playerFacilities.getOrDefault(playerId, new ConcurrentHashMap<>());
        List<Map<String, Object>> grants = new ArrayList<>();
        long totalItems = 0;
        for (PlayerFacility pf : owned.values()) {
            FacilityDef def = defs.get(pf.facilityId());
            if (def == null) {
                continue;
            }
            long elapsed = Math.max(0, nowMs - pf.lastClaimMs());
            int hours = (int) (elapsed / 3_600_000L);
            if (hours <= 0) {
                continue;
            }
            int count = hours * def.outputPerHour() * pf.level();
            totalItems += count;
            grants.add(Map.of("itemId", def.outputItemId(), "count", count,
                    "facilityId", pf.facilityId(), "hours", hours));
            owned.put(pf.facilityId(),
                    new PlayerFacility(pf.facilityId(), pf.level(), nowMs, pf.builtAtMs()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("grantPlans", grants);
        body.put("totalItems", totalItems);
        body.put("idempotencyKey", "auto_claim:" + playerId + ":" + nowMs / 60_000L);
        return body;
    }

    public Map<String, Object> status(long playerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("facilities", playerFacilities.getOrDefault(playerId, new ConcurrentHashMap<>())
                .values().stream()
                .map(pf -> {
                    FacilityDef def = defs.get(pf.facilityId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("facilityId", pf.facilityId());
                    row.put("label", def == null ? pf.facilityId() : def.label());
                    row.put("level", pf.level());
                    row.put("outputPerHour", def == null ? 0 : def.outputPerHour() * pf.level());
                    return row;
                })
                .toList());
        return body;
    }
}
