package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.update.PatchDiffPlanner;
import cn.itcast.demo.mymmorpg.model.update.ResourceFileEntry;
import cn.itcast.demo.mymmorpg.model.update.VersionManifestPayload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CDN 预热与多平台差分：开服前把热门补丁推到边缘节点，降低源站冲击。
 */
@Service
public class CdnPreheatService {

    public record PreheatJob(
            String jobId,
            PatchDiffPlanner.Platform platform,
            int versionNumber,
            List<String> urls,
            String status,
            long createdAtMs,
            long completedAtMs) {
    }

    private final ConcurrentHashMap<String, PreheatJob> jobs = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private volatile String cdnBaseUrl = "https://cdn.example.com/mmorpg/";

    public void configureCdnBase(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.cdnBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        }
    }

    public Map<String, Object> planPlatformDiff(
            PatchDiffPlanner.Platform platform,
            int fromVersion,
            int toVersion,
            VersionManifestPayload oldManifest,
            VersionManifestPayload newManifest,
            boolean forceFull) {
        List<ResourceFileEntry> oldFiles = flatten(oldManifest);
        List<ResourceFileEntry> newFiles = flatten(newManifest);
        PatchDiffPlanner.DiffPlan plan = PatchDiffPlanner.plan(
                platform, fromVersion, toVersion, oldFiles, newFiles, forceFull);
        return PatchDiffPlanner.toView(plan);
    }

    /**
     * 模拟向 CDN 边缘节点提交预热（真实环境对接厂商 Purge/Prefetch API）。
     */
    public Map<String, Object> preheat(PatchDiffPlanner.Platform platform, int versionNumber, List<String> paths) {
        List<String> urls = new ArrayList<>();
        if (paths != null) {
            for (String path : paths) {
                if (path == null || path.isBlank()) {
                    continue;
                }
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    urls.add(path);
                } else {
                    urls.add(cdnBaseUrl + path.replaceFirst("^/+", ""));
                }
            }
        }
        String jobId = "cdn-" + seq.incrementAndGet();
        long now = System.currentTimeMillis();
        PreheatJob job = new PreheatJob(jobId, platform, versionNumber, List.copyOf(urls), "SUBMITTED", now, 0L);
        jobs.put(jobId, job);
        // 本地演示：立即标记完成
        PreheatJob done = new PreheatJob(jobId, platform, versionNumber, job.urls(), "DONE", now, System.currentTimeMillis());
        jobs.put(jobId, done);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("jobId", jobId);
        body.put("platform", platform.name());
        body.put("versionNumber", versionNumber);
        body.put("urlCount", urls.size());
        body.put("status", done.status());
        body.put("cdnBaseUrl", cdnBaseUrl);
        return body;
    }

    public Map<String, Object> jobStatus(String jobId) {
        PreheatJob job = jobs.get(jobId);
        if (job == null) {
            return Map.of("ok", false, "error", "job_not_found");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("jobId", job.jobId());
        m.put("platform", job.platform().name());
        m.put("versionNumber", job.versionNumber());
        m.put("status", job.status());
        m.put("urlCount", job.urls().size());
        m.put("createdAtMs", job.createdAtMs());
        m.put("completedAtMs", job.completedAtMs());
        return m;
    }

    private static List<ResourceFileEntry> flatten(VersionManifestPayload manifest) {
        if (manifest == null) {
            return List.of();
        }
        List<ResourceFileEntry> all = new ArrayList<>();
        if (manifest.resourcePacks != null) {
            all.addAll(manifest.resourcePacks);
        }
        if (manifest.audioLanguagePacks != null) {
            all.addAll(manifest.audioLanguagePacks);
        }
        if (manifest.configFiles != null) {
            all.addAll(manifest.configFiles);
        }
        return all;
    }
}
