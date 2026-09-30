package cn.itcast.demo.mymmorpg.world.ecosystem;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生态行为状态机：非战斗时优先执行进食/巡逻/逃逸/睡眠（模拟 behavior_tree_eco）。
 * 定时 Tick（默认 5s）驱动种群作息。
 */
@Service
public class EcosystemBehaviorService {

    public static final long DEFAULT_TICK_MS = 5_000L;

    public enum EcoState {
        FEED, PATROL, FLEE, SLEEP, MIGRATE
    }

    public record CreatureEcoProfile(
            String creatureUid,
            String species,
            String regionId,
            float x, float y, float z,
            boolean diurnal,
            float fearRadiusM) {
        public CreatureEcoProfile {
            creatureUid = creatureUid == null ? "" : creatureUid.trim();
            species = species == null ? "wildlife" : species.trim();
            regionId = regionId == null ? "" : regionId.trim();
            fearRadiusM = fearRadiusM <= 0 ? 12f : fearRadiusM;
        }
    }

    private final ConcurrentHashMap<String, CreatureEcoProfile> profiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EcoState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastTickMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> killCountBySpecies = new ConcurrentHashMap<>();

    public void register(CreatureEcoProfile profile) {
        if (profile == null || profile.creatureUid().isBlank()) {
            return;
        }
        profiles.put(profile.creatureUid(), profile);
        states.putIfAbsent(profile.creatureUid(), EcoState.PATROL);
    }

    public EcoState stateOf(String creatureUid) {
        return states.getOrDefault(creatureUid == null ? "" : creatureUid.trim(), EcoState.PATROL);
    }

    public CreatureEcoProfile profileOf(String creatureUid) {
        return profiles.get(creatureUid == null ? "" : creatureUid.trim());
    }

    /** 玩家靠近 → 惧怕逃逸；距离恢复后回到巡逻。 */
    public Map<String, Object> onPlayerProximity(
            String creatureUid, float playerDistM, boolean inCombat) {
        CreatureEcoProfile p = profiles.get(creatureUid == null ? "" : creatureUid.trim());
        if (p == null) {
            return Map.of("ok", false, "error", "creature_not_found");
        }
        if (inCombat) {
            return Map.of("ok", true, "ecoPaused", true, "reason", "combat_bt_priority");
        }
        EcoState next = playerDistM <= p.fearRadiusM() ? EcoState.FLEE : EcoState.PATROL;
        states.put(p.creatureUid(), next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("creatureUid", p.creatureUid());
        body.put("state", next.name());
        body.put("fearRadiusM", p.fearRadiusM());
        body.put("playerDistM", playerDistM);
        return body;
    }

    /**
     * 生态 Tick：按昼夜切换 SLEEP/FEED/PATROL；种群过密触发 MIGRATE。
     */
    public Map<String, Object> tick(String creatureUid, int hourOfDay, long nowMs, boolean inCombat) {
        CreatureEcoProfile p = profiles.get(creatureUid == null ? "" : creatureUid.trim());
        if (p == null) {
            return Map.of("ok", false, "error", "creature_not_found");
        }
        if (inCombat) {
            return Map.of("ok", true, "skipped", true, "reason", "combat");
        }
        long last = lastTickMs.getOrDefault(p.creatureUid(), 0L);
        if (nowMs - last < DEFAULT_TICK_MS && last > 0) {
            return Map.of("ok", true, "skipped", true, "reason", "tick_cooldown",
                    "state", stateOf(p.creatureUid()).name());
        }
        lastTickMs.put(p.creatureUid(), nowMs);
        boolean night = hourOfDay >= 20 || hourOfDay < 6;
        EcoState next;
        if (p.diurnal() && night) {
            next = EcoState.SLEEP;
        } else if (!p.diurnal() && !night) {
            next = EcoState.SLEEP;
        } else if ((nowMs / DEFAULT_TICK_MS) % 7 == 0) {
            next = EcoState.FEED;
        } else if (speciesDensity(p.species(), p.regionId()) > 8) {
            next = EcoState.MIGRATE;
        } else {
            next = EcoState.PATROL;
        }
        if (states.get(p.creatureUid()) == EcoState.FLEE) {
            next = EcoState.FLEE;
        }
        states.put(p.creatureUid(), next);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("creatureUid", p.creatureUid());
        body.put("state", next.name());
        body.put("behaviorTree", "behavior_tree_eco.xml");
        body.put("hourOfDay", hourOfDay);
        body.put("priority", "eco_over_combat_bt");
        return body;
    }

    public Map<String, Object> tickRegion(String regionId, int hourOfDay, long nowMs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CreatureEcoProfile p : profiles.values()) {
            if (!p.regionId().equals(regionId == null ? "" : regionId.trim())) {
                continue;
            }
            rows.add(tick(p.creatureUid(), hourOfDay, nowMs, false));
        }
        return Map.of("ok", true, "regionId", regionId, "ticked", rows.size(), "results", rows);
    }

    public void recordKill(String species) {
        String key = species == null ? "" : species.trim();
        killCountBySpecies.merge(key, 1, Integer::sum);
    }

    public int killCount(String species) {
        return killCountBySpecies.getOrDefault(species == null ? "" : species.trim(), 0);
    }

    private int speciesDensity(String species, String regionId) {
        int n = 0;
        for (CreatureEcoProfile p : profiles.values()) {
            if (p.species().equals(species) && p.regionId().equals(regionId)) {
                n++;
            }
        }
        return n;
    }

    public List<Map<String, Object>> listSnapshots() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CreatureEcoProfile p : profiles.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("creatureUid", p.creatureUid());
            row.put("species", p.species());
            row.put("regionId", p.regionId());
            row.put("state", stateOf(p.creatureUid()).name());
            out.add(row);
        }
        return out;
    }

    public List<CreatureEcoProfile> listProfiles() {
        return List.copyOf(profiles.values());
    }
}
