package cn.itcast.demo.mymmorpg.world.sideplay;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大世界即时料理：野外篝火消耗采集物制作临时 Buff 食物，串联采集→制作→战斗。
 */
@Service
public class WorldCookingService {

    public record Recipe(
            String recipeId,
            String name,
            Map<String, Integer> ingredients,
            String buffId,
            int buffSeconds,
            String resultItemId) {

        public Recipe {
            ingredients = ingredients == null ? Map.of() : Map.copyOf(ingredients);
            buffSeconds = Math.max(30, buffSeconds);
        }
    }

    public record Campfire(
            String campfireId,
            int sceneId,
            float x, float y, float z,
            float radius) {
    }

    private final ConcurrentHashMap<String, Recipe> recipes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Campfire> campfires = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Integer>> bags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Map<String, Object>> activeBuffs = new ConcurrentHashMap<>();

    public void registerRecipe(Recipe recipe) {
        recipes.put(recipe.recipeId(), recipe);
    }

    public void registerCampfire(Campfire campfire) {
        campfires.put(campfire.campfireId(), campfire);
    }

    public void grantIngredient(long playerId, String itemId, int count) {
        bags.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .merge(itemId, Math.max(1, count), Integer::sum);
    }

    public Map<String, Object> cook(
            long playerId, String campfireId, String recipeId,
            float px, float pz, long nowMs) {
        Campfire fire = campfires.get(campfireId);
        if (fire == null) {
            return Map.of("ok", false, "error", "campfire_not_found");
        }
        float dx = px - fire.x();
        float dz = pz - fire.z();
        if (Math.sqrt(dx * dx + dz * dz) > fire.radius()) {
            return Map.of("ok", false, "error", "too_far_from_campfire");
        }
        Recipe recipe = recipes.get(recipeId);
        if (recipe == null) {
            return Map.of("ok", false, "error", "recipe_not_found");
        }
        Map<String, Integer> bag = bags.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());
        for (Map.Entry<String, Integer> need : recipe.ingredients().entrySet()) {
            if (bag.getOrDefault(need.getKey(), 0) < need.getValue()) {
                return Map.of("ok", false, "error", "missing_ingredient",
                        "itemId", need.getKey(), "need", need.getValue());
            }
        }
        for (Map.Entry<String, Integer> need : recipe.ingredients().entrySet()) {
            bag.merge(need.getKey(), -need.getValue(), Integer::sum);
        }
        long expire = nowMs + recipe.buffSeconds() * 1000L;
        Map<String, Object> buff = new LinkedHashMap<>();
        buff.put("buffId", recipe.buffId());
        buff.put("recipeId", recipeId);
        buff.put("expireAtMs", expire);
        buff.put("resultItemId", recipe.resultItemId());
        activeBuffs.put(playerId, buff);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("playerId", playerId);
        body.put("campfireId", campfireId);
        body.put("recipeId", recipeId);
        body.put("name", recipe.name());
        body.put("buff", buff);
        body.put("bag", Map.copyOf(bag));
        body.put("grantPlans", List.of(Map.of("itemId", recipe.resultItemId(), "count", 1)));
        body.put("idempotencyKey", "cook:" + playerId + ":" + recipeId + ":" + nowMs);
        return body;
    }

    public Map<String, Object> activeBuff(long playerId, long nowMs) {
        Map<String, Object> buff = activeBuffs.get(playerId);
        if (buff == null) {
            return Map.of("ok", true, "active", false);
        }
        long expire = ((Number) buff.getOrDefault("expireAtMs", 0L)).longValue();
        if (expire <= nowMs) {
            activeBuffs.remove(playerId);
            return Map.of("ok", true, "active", false, "expired", true);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("active", true);
        body.put("buff", buff);
        body.put("remainMs", expire - nowMs);
        return body;
    }

    public List<Map<String, Object>> listRecipes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Recipe r : recipes.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recipeId", r.recipeId());
            m.put("name", r.name());
            m.put("ingredients", r.ingredients());
            m.put("buffId", r.buffId());
            m.put("buffSeconds", r.buffSeconds());
            m.put("resultItemId", r.resultItemId());
            out.add(m);
        }
        return out;
    }
}
