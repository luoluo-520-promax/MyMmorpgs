package cn.itcast.demo.mymmorpg.ai.recommend;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class RecommendationEngineTest {

    @Test
    public void recommendByProfile() {
        RecommendationEngine engine = new RecommendationEngine();
        engine.upsertItem(new RecommendationEngine.CatalogItem(
                "sword_a", "equip", Map.of("combat", 0.9), List.of("combat")));
        engine.upsertItem(new RecommendationEngine.CatalogItem(
                "pack_b", "shop", Map.of("shop", 0.8), List.of("shop")));
        engine.recordInteraction(42, "sword_a", "combat");
        engine.recordInteraction(42, "sword_a", "combat");
        List<RecommendationEngine.ScoredItem> items = engine.recommend(42, "equip", 3);
        Assert.assertFalse(items.isEmpty());
        Assert.assertEquals(items.get(0).itemId(), "sword_a");
        Map<String, Object> payload = engine.recommendPayload(42, null, 5);
        Assert.assertTrue((Boolean) payload.get("ok"));
    }
}
