package cn.itcast.demo.mymmorpg.world.sideplay;

import cn.itcast.demo.mymmorpg.world.explore.CollectibleService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生物驯服后功能：跟随采集模式，harvest_affinity 提升 Collectible 结算并直入背包。
 */
@Service
public class CreatureUtilityService {

    public static final String MODE_FOLLOW_HARVEST = "FOLLOW_HARVEST";

    private final CreatureCatchService creatures;
    private final ConcurrentHashMap<Long, String> followMode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> harvestAffinity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Integer>> autoBag = new ConcurrentHashMap<>();

    public CreatureUtilityService() {
        this(new CreatureCatchService());
    }

    public CreatureUtilityService(CreatureCatchService creatures) {
        this.creatures = creatures == null ? new CreatureCatchService() : creatures;
    }

    public void setHarvestAffinity(String species, double bonusRatio) {
        harvestAffinity.put(species == null ? "" : species.trim(), Math.max(0, Math.min(1.0, bonusRatio)));
    }

    public Map<String, Object> enableFollowHarvest(long playerId, String petInstanceId) {
        List<CreatureCatchService.OwnedPet> pets = creatures.listPets(playerId);
        CreatureCatchService.OwnedPet found = null;
        for (CreatureCatchService.OwnedPet p : pets) {
            if (p.instanceId().equals(petInstanceId)) {
                found = p;
                break;
            }
        }
        if (found == null) {
            return Map.of("ok", false, "error", "pet_not_found");
        }
        followMode.put(playerId, MODE_FOLLOW_HARVEST + ":" + petInstanceId);
        double affinity = harvestAffinity.getOrDefault(found.species(), 0.2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("mode", MODE_FOLLOW_HARVEST);
        body.put("petInstanceId", petInstanceId);
        body.put("species", found.species());
        body.put("harvestAffinity", affinity);
        body.put("bonusRatio", affinity);
        return body;
    }

    public Map<String, Object> disableFollowHarvest(long playerId) {
        followMode.remove(playerId);
        return Map.of("ok", true, "mode", "NONE");
    }

    /**
     * 采集结算钩子：额外产出直入背包，无需手动拾取。
     */
    public Map<String, Object> applyHarvestBonus(
            long playerId, Map<String, Object> collectResult) {
        String mode = followMode.get(playerId);
        if (mode == null || !mode.startsWith(MODE_FOLLOW_HARVEST)) {
            if (!Boolean.TRUE.equals(collectResult.get("ok"))) {
                return collectResult;
            }
            Map<String, Object> passthrough = new LinkedHashMap<>(collectResult);
            passthrough.put("bonusApplied", false);
            return passthrough;
        }
        if (!Boolean.TRUE.equals(collectResult.get("ok"))) {
            return collectResult;
        }
        String petId = mode.substring(MODE_FOLLOW_HARVEST.length() + 1);
        double ratio = 0.2;
        for (CreatureCatchService.OwnedPet p : creatures.listPets(playerId)) {
            if (p.instanceId().equals(petId)) {
                ratio = harvestAffinity.getOrDefault(p.species(), 0.2);
                break;
            }
        }
        List<Map<String, Object>> bonusPlans = new ArrayList<>();
        Object grants = collectResult.get("grantPlans");
        if (grants instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> g)) {
                    continue;
                }
                String itemId = String.valueOf(g.get("itemId"));
                int count = g.get("count") instanceof Number n ? n.intValue() : 1;
                int bonus = Math.max(1, (int) Math.round(count * ratio));
                // 双倍掉落：至少 bonus_ratio 份额
                if (ratio >= 0.2) {
                    bonus = Math.max(bonus, count);
                }
                Map<String, Integer> bag = autoBag.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
                bag.merge(itemId, bonus, Integer::sum);
                bonusPlans.add(Map.of(
                        "itemId", itemId,
                        "count", bonus,
                        "source", "creature_follow_harvest",
                        "autoLoot", true));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("bonusApplied", true);
        body.put("bonusRatio", ratio);
        body.put("bonusGrantPlans", bonusPlans);
        body.put("autoBag", Map.copyOf(autoBag.getOrDefault(playerId, Map.of())));
        body.put("base", collectResult);
        body.put("note", "extra_yield_auto_looted");
        return body;
    }

    public Map<String, Integer> autoInventory(long playerId) {
        return Map.copyOf(autoBag.getOrDefault(playerId, Map.of()));
    }

    /** 兼容 CollectibleService 调用签名。 */
    public Map<String, Object> wrapCollect(
            CollectibleService collectibles,
            long playerId, String collectibleId, float x, float y, float z) {
        Map<String, Object> base = collectibles.collect(playerId, collectibleId, x, y, z);
        return applyHarvestBonus(playerId, base);
    }
}
