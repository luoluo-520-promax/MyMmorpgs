package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.model.update.PatchDiffPlanner;
import cn.itcast.demo.mymmorpg.model.update.ResourceFileEntry;
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload;
import cn.itcast.demo.mymmorpg.service.CdnPreheatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多平台差分补丁与 CDN 预热运维接口。
 */
@RestController
@RequestMapping("/internal/update")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "update-service")
public class InternalUpdatePatchController {

    private final CdnPreheatService cdnPreheatService;

    public InternalUpdatePatchController(CdnPreheatService cdnPreheatService) {
        this.cdnPreheatService = cdnPreheatService;
    }

    @PostMapping("/patch/diff")
    public Map<String, Object> patchDiff(@RequestBody Map<String, Object> body) {
        PatchDiffPlanner.Platform platform = PatchDiffPlanner.Platform.valueOf(
                String.valueOf(body.getOrDefault("platform", "ANDROID")));
        int from = asInt(body.get("fromVersion"), 0);
        int to = asInt(body.get("toVersion"), 1);
        boolean forceFull = Boolean.TRUE.equals(body.get("forceFull"));
        VersionManifestPayload oldM = toManifest(body.get("oldFiles"));
        VersionManifestPayload newM = toManifest(body.get("newFiles"));
        return cdnPreheatService.planPlatformDiff(platform, from, to, oldM, newM, forceFull);
    }

    @PostMapping("/cdn/preheat")
    public Map<String, Object> preheat(@RequestBody Map<String, Object> body) {
        PatchDiffPlanner.Platform platform = PatchDiffPlanner.Platform.valueOf(
                String.valueOf(body.getOrDefault("platform", "ANDROID")));
        int version = asInt(body.get("versionNumber"), 0);
        @SuppressWarnings("unchecked")
        List<String> paths = body.get("paths") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        if (body.get("cdnBaseUrl") != null) {
            cdnPreheatService.configureCdnBase(String.valueOf(body.get("cdnBaseUrl")));
        }
        return cdnPreheatService.preheat(platform, version, paths);
    }

    @GetMapping("/cdn/preheat/status")
    public Map<String, Object> preheatStatus(@RequestParam String jobId) {
        return cdnPreheatService.jobStatus(jobId);
    }

    @SuppressWarnings("unchecked")
    private static VersionManifestPayload toManifest(Object filesObj) {
        VersionManifestPayload m = new VersionManifestPayload();
        if (!(filesObj instanceof List<?> list)) {
            return m;
        }
        List<ResourceFileEntry> entries = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> map)) {
                continue;
            }
            ResourceFileEntry e = new ResourceFileEntry();
            Object path = map.get("path");
            Object checksum = map.get("checksum");
            Object downloadUrl = map.get("downloadUrl");
            e.path = path == null ? "" : String.valueOf(path);
            e.checksum = checksum == null ? "" : String.valueOf(checksum);
            e.size = asLong(map.get("size"));
            e.downloadUrl = downloadUrl == null ? "" : String.valueOf(downloadUrl);
            entries.add(e);
        }
        m.resourcePacks = entries;
        return m;
    }

    private static int asInt(Object v, int dft) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? dft : Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return dft;
        }
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return v == null ? 0L : Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }
}
