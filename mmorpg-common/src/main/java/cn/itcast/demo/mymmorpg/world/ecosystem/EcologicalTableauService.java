package cn.itcast.demo.mymmorpg.world.ecosystem;

import cn.itcast.demo.mymmorpg.world.narrative.CoopNarrativeProxy;
import cn.itcast.demo.mymmorpg.world.narrative.PlayerChronicleService;
import cn.itcast.demo.mymmorpg.world.time.WorldTimeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P19 生态纪录片引擎：纯后台微叙事，不涉及战斗逻辑。
 */
@Service
public class EcologicalTableauService {

    public static final long SCAN_INTERVAL_MS = 600_000L;
    public static final float PREDATOR_PREY_RADIUS_M = 20f;
    public static final double CARCASS_SPAWN_CHANCE = 0.30;

    public record CarcassSpawn(
            String id, String predatorUid, String preyUid,
            float x, float y, float z, long createdAtMs, boolean collected) {
    }

    public record HiddenDigSite(
            long playerId, float x, float y, float z,
            String narrative, boolean excavated) {
    }

    private final ConcurrentHashMap<String, CarcassSpawn> carcasses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, HiddenDigSite> digSites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> treePassCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> weatherMemorialTriggered = new ConcurrentHashMap<>();
    private long lastScanMs;
    private EcosystemBehaviorService ecosystem;
    private CoopNarrativeProxy coopNarrative;
    private PlayerChronicleService chronicle;

    public void bind(EcosystemBehaviorService ecosystem, CoopNarrativeProxy narrative,
                     PlayerChronicleService chronicle) {
        this.ecosystem = ecosystem;
        this.coopNarrative = narrative;
        this.chronicle = chronicle;
    }

    /**
     * 每 10 分钟扫描捕食链，30% 概率生成 CARCASS。
     */
    public Map<String, Object> scanPredatorPrey(String regionId, long nowMs) {
        if (nowMs - lastScanMs < SCAN_INTERVAL_MS && lastScanMs > 0) {
            return Map.of("ok", true, "skipped", true, "reason", "scan_cooldown");
        }
        lastScanMs = nowMs;
        if (ecosystem == null) {
            return Map.of("ok", false, "error", "ecosystem_not_bound");
        }
        List<Map<String, Object>> spawned = new ArrayList<>();
        List<EcosystemBehaviorService.CreatureEcoProfile> profiles = ecosystem.listProfiles();
        for (EcosystemBehaviorService.CreatureEcoProfile predator : profiles) {
            if (!predator.regionId().equals(regionId == null ? "" : regionId.trim())) {
                continue;
            }
            if (!isPredator(predator.species())) {
                continue;
            }
            for (EcosystemBehaviorService.CreatureEcoProfile prey : profiles) {
                if (predator.creatureUid().equals(prey.creatureUid()) || !isPrey(prey.species())) {
                    continue;
                }
                float dx = predator.x() - prey.x();
                float dz = predator.z() - prey.z();
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist > PREDATOR_PREY_RADIUS_M) {
                    continue;
                }
                if (Math.random() > CARCASS_SPAWN_CHANCE) {
                    continue;
                }
                String id = "carcass:" + predator.creatureUid() + ":" + nowMs;
                float cx = (predator.x() + prey.x()) / 2f;
                float cz = (predator.z() + prey.z()) / 2f;
                CarcassSpawn c = new CarcassSpawn(
                        id, predator.creatureUid(), prey.creatureUid(),
                        cx, predator.y(), cz, nowMs, false);
                carcasses.put(id, c);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("event", "CARCASS_SPAWN");
                row.put("carcassId", id);
                row.put("x", cx);
                row.put("y", predator.y());
                row.put("z", cz);
                row.put("collectOnce", true);
                spawned.add(row);
            }
        }
        return Map.of("ok", true, "regionId", regionId, "spawned", spawned.size(), "carcasses", spawned);
    }

    /**
     * 玩家连续 3 天下午 3 点路过特定树 → 隐藏挖掘点 + 回忆旁白。
     */
    public Map<String, Object> onPassLandmarkTree(
            long playerId, String treeId, float x, float y, float z,
            int hourOfDay, int dayOfYear, long nowMs) {
        String key = playerId + ":" + (treeId == null ? "tree" : treeId);
        if (hourOfDay == 15) {
            treePassCount.merge(key, 1, Integer::sum);
        }
        int passes = treePassCount.getOrDefault(key, 0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("treeId", treeId);
        body.put("passCount", passes);
        if (passes >= 3 && !digSites.containsKey(playerId)) {
            String narrative = "我好像在这里埋过东西……（" + Math.round(x) + "," + Math.round(z) + "）";
            HiddenDigSite site = new HiddenDigSite(playerId, x, y - 0.5f, z, narrative, false);
            digSites.put(playerId, site);
            body.put("event", "HIDDEN_NARRATIVE");
            body.put("narrative", narrative);
            body.put("digSiteX", site.x());
            body.put("digSiteY", site.y());
            body.put("digSiteZ", site.z());
            body.put("reward", "landscape_postcard");
            body.put("noQuestTracker", true);
            body.put("noStatReward", true);
        }
        return body;
    }

    public Map<String, Object> excavate(long playerId, long nowMs) {
        HiddenDigSite site = digSites.get(playerId);
        if (site == null) {
            return Map.of("ok", false, "error", "no_dig_site");
        }
        if (site.excavated()) {
            return Map.of("ok", false, "error", "already_excavated");
        }
        digSites.put(playerId, new HiddenDigSite(
                playerId, site.x(), site.y(), site.z(), site.narrative(), true));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "EXCAVATE_POSTCARD");
        body.put("item", "landscape_postcard");
        body.put("narrative", site.narrative());
        return body;
    }

    /**
     * 第一场雨/雪：联机世界范围轻微过场 + Chronicle 成就。
     */
    public Map<String, Object> triggerWeatherMemorial(
            WorldTimeService.Weather weather, long nowMs) {
        String key = weather == null ? "CLEAR" : weather.name();
        if (weatherMemorialTriggered.putIfAbsent(key, true) != null) {
            return Map.of("ok", true, "skipped", true, "reason", "already_triggered");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "WEATHER_MEMORIAL");
        body.put("weather", key);
        body.put("cutscene", true);
        body.put("uiBlocked", false);
        body.put("cameraLookSky", true);
        body.put("photoModeEnabled", true);
        body.put("chronicleAchievement", "season_first_" + key.toLowerCase());
        if (chronicle != null) {
            body.put("chronicleRecorded", true);
        }
        if (coopNarrative != null) {
            body.put("coopBroadcast", true);
        }
        return body;
    }

    public Map<String, Object> collectCarcass(String carcassId, long playerId) {
        CarcassSpawn c = carcasses.get(carcassId == null ? "" : carcassId.trim());
        if (c == null) {
            return Map.of("ok", false, "error", "carcass_not_found");
        }
        if (c.collected()) {
            return Map.of("ok", false, "error", "already_collected");
        }
        carcasses.put(c.id(), new CarcassSpawn(
                c.id(), c.predatorUid(), c.preyUid(), c.x(), c.y(), c.z(), c.createdAtMs(), true));
        return Map.of("ok", true, "carcassId", c.id(), "loot", "predator_remains", "collectOnce", true);
    }

    private static boolean isPredator(String species) {
        if (species == null) {
            return false;
        }
        String s = species.toLowerCase();
        return s.contains("wolf") || s.contains("fox") || s.contains("eagle") || s.contains("predator");
    }

    private static boolean isPrey(String species) {
        if (species == null) {
            return false;
        }
        String s = species.toLowerCase();
        return s.contains("rabbit") || s.contains("deer") || s.contains("prey") || s.contains("boar");
    }
}
