package cn.itcast.demo.mymmorpg.model.update;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PatchPlanBuilderTest {

    @Test
    public void build_downloadsMissingAndMismatchedFiles() {
        VersionManifestPayload manifest = manifest();
        PatchPlanBuilder.PatchPlan plan = PatchPlanBuilder.build(manifest, 9500, Map.of(
                "assets/ui/main.bundle", "aaa111",
                "config/activity.json", "wrong"));

        assertThat(plan.forceUpdate()).isFalse();
        assertThat(plan.downloadFiles()).extracting(e -> e.path)
                .contains("assets/scene/starter.bundle", "config/activity.json");
        assertThat(plan.deleteFiles()).containsExactly("assets/ui/old_main.bundle");
    }

    @Test
    public void build_forceUpdateWhenBelowMinVersion() {
        VersionManifestPayload manifest = manifest();
        PatchPlanBuilder.PatchPlan plan = PatchPlanBuilder.build(manifest, 8000, Map.of());
        assertThat(plan.forceUpdate()).isTrue();
    }

    @Test
    public void findInvalidFiles_returnsMismatchPaths() {
        VersionManifestPayload manifest = manifest();
        List<String> invalid = PatchPlanBuilder.findInvalidFiles(manifest, Map.of(
                "assets/ui/main.bundle", "aaa111",
                "audio/zh-CN/voice.pak", "bad"));
        assertThat(invalid).containsExactly("audio/zh-CN/voice.pak");
    }

    private VersionManifestPayload manifest() {
        VersionManifestPayload m = new VersionManifestPayload();
        m.versionNumber = 10000;
        m.minClientVersionNumber = 9000;
        m.deleteFiles = List.of("assets/ui/old_main.bundle");
        m.resourcePacks = List.of(entry("assets/ui/main.bundle", "aaa111"),
                entry("assets/scene/starter.bundle", "bbb222"));
        m.audioLanguagePacks = List.of(entry("audio/zh-CN/voice.pak", "ccc333"));
        m.configFiles = List.of(entry("config/activity.json", "eee555"));
        return m;
    }

    private ResourceFileEntry entry(String path, String checksum) {
        ResourceFileEntry e = new ResourceFileEntry();
        e.path = path;
        e.checksum = checksum;
        return e;
    }
}
