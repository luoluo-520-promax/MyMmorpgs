package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.web.InternalOpenWorldController;
import cn.itcast.demo.mymmorpg.world.content.PlayerConfigContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 灰度配置 Internal API 全链路：stage → gray-publish → resolve → gray-rollback → full publish。
 */
public class OpenWorldGrayConfigBusinessFlowTest {

    private OpenWorldRuntimeService openWorld;
    private InternalOpenWorldController api;

    @BeforeMethod
    public void setUp() {
        openWorld = new OpenWorldRuntimeService();
        api = new InternalOpenWorldController(openWorld);
    }

    @Test
    public void grayPublishApi_thenRollback_thenFullPublish() {
        openWorld.gameplay().configPatch().upsert("siege-cell", "SIEGE", Map.of("cap", 10), "base");

        Map<String, Object> staged = api.configStage(body(
                "gridCell", "siege-cell",
                "kind", "SIEGE",
                "gitCommitSha", "abc123456789",
                "configVersion", "p20-gray",
                "payload", Map.of("cap", 20)));
        assertThat(staged.get("ok")).isEqualTo(true);
        assertThat(staged.get("staged")).isEqualTo(true);

        Map<String, Object> gray = api.configGrayPublish(body(
                "label", "p20-siege",
                "zoneId", 1001,
                "accountModBase", 10,
                "accountModRemainder", 0,
                "trafficPercent", 100));
        assertThat(gray.get("ok")).isEqualTo(true);
        assertThat(gray.get("gray")).isEqualTo(true);
        assertThat(gray.get("grayLabel")).isEqualTo("p20-siege");

        Map<String, Object> matched = openWorld.gameplay().configPatch().resolveForPlayer(
                new PlayerConfigContext(1L, 100L, 1001, false), "siege-cell");
        assertThat(matched.get("fromGray")).isEqualTo(true);
        assertThat(((Map<?, ?>) matched.get("payload")).get("cap")).isEqualTo(20);

        Map<String, Object> unmatched = openWorld.gameplay().configPatch().resolveForPlayer(
                new PlayerConfigContext(2L, 101L, 2002, false), "siege-cell");
        assertThat(unmatched.get("fromGray")).isNull();
        assertThat(((Map<?, ?>) unmatched.get("payload")).get("cap")).isEqualTo(10);

        Map<String, Object> rolled = api.configGrayRollback(Map.of("grayLabel", "p20-siege"));
        assertThat(rolled.get("ok")).isEqualTo(true);
        assertThat(openWorld.gameplay().configPatch().resolveForPlayer(
                new PlayerConfigContext(1L, 100L, 1001, false), "siege-cell").get("fromGray")).isNull();

        api.configStage(body(
                "gridCell", "siege-cell",
                "kind", "SIEGE",
                "gitCommitSha", "fff123456789",
                "configVersion", "p20-live",
                "payload", Map.of("cap", 30)));
        Map<String, Object> published = api.configPublish();
        assertThat(published.get("ok")).isEqualTo(true);
        assertThat(openWorld.gameplay().configPatch().getCell("siege-cell").get("payload"))
                .isEqualTo(Map.of("cap", 30));
    }

    private static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
