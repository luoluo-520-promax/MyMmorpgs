package cn.itcast.demo.mymmorpg.model.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 多平台补丁差分：按平台拆分增量文件，避免开服全量冲击。
 */
public final class PatchDiffPlanner {

    public enum Platform {
        ANDROID, IOS, WINDOWS, MAC, HARMONY
    }

    public record DiffEntry(String path, String checksum, long sizeBytes, String cdnUrl, int fromVersion, int toVersion) {
    }

    public record DiffPlan(
            Platform platform,
            int fromVersion,
            int toVersion,
            List<DiffEntry> patchFiles,
            long totalBytes,
            boolean forceFull) {
    }

    private PatchDiffPlanner() {
    }

    /**
     * 根据新旧清单生成平台差分：仅包含 checksum 变化或新增文件。
     */
    public static DiffPlan plan(
            Platform platform,
            int fromVersion,
            int toVersion,
            List<ResourceFileEntry> oldFiles,
            List<ResourceFileEntry> newFiles,
            boolean forceFull) {
        Objects.requireNonNull(platform, "platform");
        List<ResourceFileEntry> oldList = oldFiles == null ? List.of() : oldFiles;
        List<ResourceFileEntry> newList = newFiles == null ? List.of() : newFiles;
        Map<String, ResourceFileEntry> oldIndex = new LinkedHashMap<>();
        for (ResourceFileEntry e : oldList) {
            oldIndex.put(e.path, e);
        }
        List<DiffEntry> diffs = new ArrayList<>();
        long total = 0L;
        for (ResourceFileEntry neu : newList) {
            ResourceFileEntry old = oldIndex.get(neu.path);
            if (forceFull || old == null || !Objects.equals(old.checksum, neu.checksum)) {
                long size = neu.size > 0 ? neu.size : 0L;
                diffs.add(new DiffEntry(neu.path, neu.checksum, size, neu.downloadUrl, fromVersion, toVersion));
                total += size;
            }
        }
        return new DiffPlan(platform, fromVersion, toVersion, diffs, total, forceFull);
    }

    public static Map<String, Object> toView(DiffPlan plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("platform", plan.platform().name());
        m.put("fromVersion", plan.fromVersion());
        m.put("toVersion", plan.toVersion());
        m.put("forceFull", plan.forceFull());
        m.put("fileCount", plan.patchFiles().size());
        m.put("totalBytes", plan.totalBytes());
        List<Map<String, Object>> files = new ArrayList<>();
        for (DiffEntry e : plan.patchFiles()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("path", e.path());
            f.put("checksum", e.checksum());
            f.put("sizeBytes", e.sizeBytes());
            f.put("cdnUrl", e.cdnUrl());
            files.add(f);
        }
        m.put("files", files);
        return m;
    }
}
