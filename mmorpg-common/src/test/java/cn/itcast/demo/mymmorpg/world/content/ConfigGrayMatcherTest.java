package cn.itcast.demo.mymmorpg.world.content;

import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 灰度配置匹配与 OpenWorldConfigPatchService 灰度发布。
 */
public class ConfigGrayMatcherTest {

    private OpenWorldConfigPatchService patchService;

    @BeforeMethod
    public void setUp() {
        patchService = new OpenWorldConfigPatchService();
        patchService.upsert("cell-a", "ABYSS", Map.of("floorHp", 100), "baseline-v1");
        patchService.stagePatch("cell-a", "ABYSS", Map.of("floorHp", 200),
                "abc123def456", "gray-v2");
    }

    @Test
    public void grayPublish_matchesZoneAndAccountMod() {
        GrayConditions cond = new GrayConditions("p20-siege", 1001L, 10, 0, null, 100);
        Map<String, Object> published = patchService.publishStaging(cond, System.currentTimeMillis());
        assertThat(published.get("ok")).isEqualTo(true);
        assertThat(published.get("gray")).isEqualTo(true);

        PlayerConfigContext matched = new PlayerConfigContext(1L, 100L, 1001, false);
        Map<String, Object> cell = patchService.resolveForPlayer(matched, "cell-a");
        assertThat(cell.get("fromGray")).isEqualTo(true);
        assertThat(((Map<?, ?>) cell.get("payload")).get("floorHp")).isEqualTo(200);

        PlayerConfigContext unmatched = new PlayerConfigContext(2L, 101L, 1002, false);
        Map<String, Object> baseline = patchService.resolveForPlayer(unmatched, "cell-a");
        assertThat(baseline.get("fromGray")).isNull();
        assertThat(((Map<?, ?>) baseline.get("payload")).get("floorHp")).isEqualTo(100);
    }

    @Test
    public void grayRollback_restoresBaselineOnly() {
        patchService.publishStaging(new GrayConditions("beta", 1001L, null, null, true, 100),
                System.currentTimeMillis());
        Map<String, Object> rolled = patchService.rollbackGray("beta");
        assertThat(rolled.get("ok")).isEqualTo(true);

        PlayerConfigContext ctx = new PlayerConfigContext(1L, 1L, 1001, true);
        Map<String, Object> cell = patchService.resolveForPlayer(ctx, "cell-a");
        assertThat(cell.get("fromGray")).isNull();
    }

    @Test
    public void configGrayMatcher_trafficPercent() {
        GrayConditions cond = new GrayConditions("pct", null, null, null, null, 10);
        assertThat(ConfigGrayMatcher.matches(cond, new PlayerConfigContext(1L, 5L, 0, false))).isTrue();
        assertThat(ConfigGrayMatcher.matches(cond, new PlayerConfigContext(1L, 55L, 0, false))).isFalse();
    }
}
