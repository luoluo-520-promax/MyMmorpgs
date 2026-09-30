package cn.itcast.demo.mymmorpg.model.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 资源热更补丁链：版本串 + 分包下载 + 断点续传元数据，可与 CDN 预热联动。
 */
public final class ResourcePatchChain {

    public record PatchNode(
            int version,
            String checksum,
            long sizeBytes,
            String cdnUrl,
            List<String> splitParts,
            boolean hotReplace) {
    }

    public record ResumeCursor(int version, String partId, long offsetBytes) {
    }

    public record DownloadPlan(
            int fromVersion,
            int toVersion,
            List<PatchNode> chain,
            long totalBytes,
            ResumeCursor resume,
            List<String> cdnPreheatUrls) {
    }

    private ResourcePatchChain() {
    }

    /**
     * 从 from 升到 to：按版本升序串联中间补丁；支持从 resume 断点续传。
     */
    public static DownloadPlan plan(
            int fromVersion,
            int toVersion,
            List<PatchNode> availablePatches,
            ResumeCursor resume) {
        if (toVersion < fromVersion) {
            throw new IllegalArgumentException("toVersion < fromVersion");
        }
        List<PatchNode> patches = availablePatches == null ? List.of() : availablePatches;
        List<PatchNode> chain = new ArrayList<>();
        long total = 0L;
        List<String> preheat = new ArrayList<>();
        for (PatchNode p : patches) {
            if (p.version() > fromVersion && p.version() <= toVersion) {
                chain.add(p);
                total += Math.max(0L, p.sizeBytes());
                if (p.cdnUrl() != null && !p.cdnUrl().isBlank()) {
                    preheat.add(p.cdnUrl());
                }
                if (p.splitParts() != null) {
                    for (String part : p.splitParts()) {
                        preheat.add(p.cdnUrl() + "/" + part);
                    }
                }
            }
        }
        chain.sort((a, b) -> Integer.compare(a.version(), b.version()));
        ResumeCursor cursor = resume;
        if (cursor != null && cursor.version() > 0) {
            chain.removeIf(p -> p.version() < cursor.version());
        }
        total = 0L;
        for (PatchNode p : chain) {
            total += Math.max(0L, p.sizeBytes());
        }
        if (cursor != null && !chain.isEmpty() && chain.get(0).version() == cursor.version()) {
            total = Math.max(0L, total - Math.max(0L, cursor.offsetBytes()));
        }
        return new DownloadPlan(fromVersion, toVersion, List.copyOf(chain), total, cursor, List.copyOf(preheat));
    }

    public static Map<String, Object> toView(DownloadPlan plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fromVersion", plan.fromVersion());
        m.put("toVersion", plan.toVersion());
        m.put("totalBytes", plan.totalBytes());
        m.put("patchCount", plan.chain().size());
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (PatchNode n : plan.chain()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("version", n.version());
            row.put("checksum", n.checksum());
            row.put("sizeBytes", n.sizeBytes());
            row.put("cdnUrl", n.cdnUrl());
            row.put("splitParts", n.splitParts() == null ? List.of() : n.splitParts());
            row.put("hotReplace", n.hotReplace());
            nodes.add(row);
        }
        m.put("chain", nodes);
        m.put("cdnPreheatUrls", plan.cdnPreheatUrls());
        if (plan.resume() != null) {
            m.put("resume", Map.of(
                    "version", plan.resume().version(),
                    "partId", Objects.toString(plan.resume().partId(), ""),
                    "offsetBytes", plan.resume().offsetBytes()));
        }
        return m;
    }
}
