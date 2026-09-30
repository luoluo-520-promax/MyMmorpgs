package cn.itcast.demo.mymmorpg.dda;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

public class BossStrategyTierTest {

    @Test
    public void mapDifficultyToTier() {
        Assert.assertEquals(BossStrategyTier.fromDifficulty(0.7).tier(), "NOVICE");
        Assert.assertEquals(BossStrategyTier.fromDifficulty(1.0).tier(), "STANDARD");
        Assert.assertEquals(BossStrategyTier.fromDifficulty(1.15).tier(), "VETERAN");
        Assert.assertEquals(BossStrategyTier.fromDifficulty(1.4).tier(), "ELITE");
        Map<String, Object> merged = BossStrategyTier.merge(
                new DifficultyEvaluator.Adjustment(1.2, 1.1, 1.0, 1.3, "test", Map.of()));
        Assert.assertEquals(merged.get("tier"), "ELITE");
        Assert.assertTrue(((Number) merged.get("damageMul")).doubleValue() <= 1.4);
    }
}
