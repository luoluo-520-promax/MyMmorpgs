package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.update.PatchDiffPlanner;
import cn.itcast.demo.mymmorpg.model.update.ResourceFileEntry;
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多平台差分补丁 + CDN 预热业务流程。
 */
public class CdnPreheatServiceTest {

    @Test
    public void platformDiff_onlyChangedFiles_thenPreheat() {
        CdnPreheatService service = new CdnPreheatService();
        service.configureCdnBase("https://cdn.test/game/");

        VersionManifestPayload oldM = new VersionManifestPayload();
        ResourceFileEntry keep = entry("a.bin", "aaa", 10);
        ResourceFileEntry changedOld = entry("b.bin", "old", 20);
        oldM.resourcePacks = List.of(keep, changedOld);

        VersionManifestPayload newM = new VersionManifestPayload();
        ResourceFileEntry changedNew = entry("b.bin", "new", 22);
        ResourceFileEntry added = entry("c.bin", "ccc", 30);
        newM.resourcePacks = List.of(keep, changedNew, added);

        Map<String, Object> diff = service.planPlatformDiff(
                PatchDiffPlanner.Platform.ANDROID, 100, 101, oldM, newM, false);
        assertThat(diff.get("fileCount")).isEqualTo(2);
        assertThat(((Number) diff.get("totalBytes")).longValue()).isEqualTo(52L);

        Map<String, Object> job = service.preheat(
                PatchDiffPlanner.Platform.ANDROID, 101, List.of("b.bin", "c.bin"));
        assertThat(job.get("ok")).isEqualTo(true);
        assertThat(job.get("status")).isEqualTo("DONE");
        assertThat(job.get("urlCount")).isEqualTo(2);

        String jobId = String.valueOf(job.get("jobId"));
        Map<String, Object> status = service.jobStatus(jobId);
        assertThat(status.get("ok")).isEqualTo(true);
        assertThat(status.get("status")).isEqualTo("DONE");
    }

    @Test
    public void forceFull_includesAllFiles() {
        CdnPreheatService service = new CdnPreheatService();
        VersionManifestPayload oldM = new VersionManifestPayload();
        oldM.resourcePacks = List.of(entry("a.bin", "aaa", 10));
        VersionManifestPayload newM = new VersionManifestPayload();
        newM.resourcePacks = List.of(entry("a.bin", "aaa", 10), entry("b.bin", "bbb", 5));

        Map<String, Object> diff = service.planPlatformDiff(
                PatchDiffPlanner.Platform.IOS, 1, 2, oldM, newM, true);
        assertThat(diff.get("forceFull")).isEqualTo(true);
        assertThat(diff.get("fileCount")).isEqualTo(2);
    }

    private static ResourceFileEntry entry(String path, String checksum, long size) {
        ResourceFileEntry e = new ResourceFileEntry();
        e.path = path;
        e.checksum = checksum;
        e.size = size;
        return e;
    }
}
