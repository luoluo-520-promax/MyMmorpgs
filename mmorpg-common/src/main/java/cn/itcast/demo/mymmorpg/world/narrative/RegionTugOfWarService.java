package cn.itcast.demo.mymmorpg.world.narrative;

import cn.itcast.demo.mymmorpg.world.explore.RegionImpactService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 区域拉锯战：阵营交付资源，周结算后胜者占领区域并获得 FACTION_BUFF。
 */
@Service
public class RegionTugOfWarService {

    public static final String FACTION_ABYSS = "深渊教团";
    public static final String FACTION_KINGDOM = "王国军";
    public static final String FACTION_BUFF = "FACTION_BUFF";

    private final ConcurrentHashMap<String, AtomicInteger> donateScore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> occupiedBy = new ConcurrentHashMap<>();
    private RegionImpactService regions;

    public RegionTugOfWarService() {
    }

    public RegionTugOfWarService(RegionImpactService regions) {
        this.regions = regions;
    }

    public void bindRegions(RegionImpactService regions) {
        this.regions = regions;
    }

    private static String scoreKey(String regionId, String faction) {
        return regionId + ":" + faction;
    }

    public Map<String, Object> donate(long playerId, String regionId, String faction, int amount) {
        if (!FACTION_ABYSS.equals(faction) && !FACTION_KINGDOM.equals(faction)) {
            return Map.of("ok", false, "error", "invalid_faction");
        }
        int add = Math.max(1, amount);
        int score = donateScore.computeIfAbsent(scoreKey(regionId, faction), k -> new AtomicInteger(0))
                .addAndGet(add);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "FACTION_DONATE");
        body.put("playerId", playerId);
        body.put("regionId", regionId);
        body.put("faction", faction);
        body.put("donated", add);
        body.put("score", score);
        return body;
    }

    public Map<String, Object> weeklySettle(String regionId) {
        int abyss = donateScore.getOrDefault(scoreKey(regionId, FACTION_ABYSS), new AtomicInteger(0)).get();
        int kingdom = donateScore.getOrDefault(scoreKey(regionId, FACTION_KINGDOM), new AtomicInteger(0)).get();
        String winner = abyss >= kingdom ? FACTION_ABYSS : FACTION_KINGDOM;
        String loser = winner.equals(FACTION_ABYSS) ? FACTION_KINGDOM : FACTION_ABYSS;
        occupiedBy.put(regionId, winner);
        if (regions != null) {
            // 胜者区域偏 SAFE，败者撤退语义由 CONTESTED 表达
            regions.forceSafety(regionId, RegionImpactService.RegionSafety.SAFE);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("regionId", regionId);
        body.put("winner", winner);
        body.put("loser", loser);
        body.put("scores", Map.of(FACTION_ABYSS, abyss, FACTION_KINGDOM, kingdom));
        body.put("winnerBuff", Map.of(
                "buff", FACTION_BUFF,
                "gatherBonus", 0.5,
                "scope", "region:" + regionId));
        body.put("loserRetreat", true);
        body.put("loserNote", "只能前往相邻区域");
        return body;
    }

    public String occupant(String regionId) {
        return occupiedBy.get(regionId);
    }

    public Map<String, Object> gatherBonus(long playerId, String regionId, String faction) {
        String occ = occupiedBy.get(regionId);
        boolean buffed = occ != null && occ.equals(faction);
        return Map.of("ok", true, "playerId", playerId, "regionId", regionId,
                "buffed", buffed, "gatherMul", buffed ? 1.5 : 1.0);
    }
}
