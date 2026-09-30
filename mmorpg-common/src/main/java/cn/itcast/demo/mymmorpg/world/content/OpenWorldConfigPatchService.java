package cn.itcast.demo.mymmorpg.world.content;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大世界配置段增量热更新：Schema Versioning + 双缓冲，绑定 Git Commit SHA。
 */
@Service
public class OpenWorldConfigPatchService {

    public record ConfigCell(
            String gridCell,
            String kind,
            Map<String, Object> payload,
            long version,
            String configVersion) {
        public ConfigCell(String gridCell, String kind, Map<String, Object> payload, long version) {
            this(gridCell, kind, payload, version, "unversioned");
        }
    }

    public record ConfigSnapshot(
            String configVersion,
            String gitCommitSha,
            Map<String, ConfigCell> cells,
            long capturedAtMs) {
    }

    private final ConcurrentHashMap<String, ConfigCell> cells = new ConcurrentHashMap<>();
    /** Redis:config:staging 语义 */
    private final ConcurrentHashMap<String, ConfigCell> staging = new ConcurrentHashMap<>();
    /** 灰度 staging：label -> gridCell -> cell */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConfigCell>> grayStaging = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GrayConditions> grayConditionsByLabel = new ConcurrentHashMap<>();
    private volatile boolean grayPublishActive;
    private volatile String activeGrayLabel = "";
    private final ConcurrentHashMap<String, Long> reloadLog = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConfigSnapshot> instanceSnapshots = new ConcurrentHashMap<>();
    private volatile String currentConfigVersion = "bootstrap";
    private volatile String stagingConfigVersion = "";
    private volatile String stagingGitSha = "";
    private long globalVersion = 1L;
    private boolean stagingReady;

    public Map<String, Object> upsert(String gridCell, String kind, Map<String, Object> payload) {
        return upsert(gridCell, kind, payload, currentConfigVersion);
    }

    public Map<String, Object> upsert(
            String gridCell, String kind, Map<String, Object> payload, String configVersion) {
        if (gridCell == null || gridCell.isBlank()) {
            return Map.of("ok", false, "error", "grid_cell_required");
        }
        long ver = ++globalVersion;
        String cv = configVersion == null || configVersion.isBlank() ? currentConfigVersion : configVersion.trim();
        ConfigCell cell = new ConfigCell(gridCell.trim(), kind == null ? "GENERIC" : kind,
                payload == null ? Map.of() : Map.copyOf(payload), ver, cv);
        cells.put(cell.gridCell(), cell);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("gridCell", cell.gridCell());
        body.put("kind", cell.kind());
        body.put("version", ver);
        body.put("configVersion", cv);
        body.put("payload", cell.payload());
        return body;
    }

    /**
     * 写入 staging 缓冲区（Redis:config:staging），不立即影响现有场景。
     */
    public Map<String, Object> stagePatch(
            String gridCell, String kind, Map<String, Object> payload,
            String gitCommitSha, String configVersion) {
        if (gridCell == null || gridCell.isBlank()) {
            return Map.of("ok", false, "error", "grid_cell_required");
        }
        if (gitCommitSha == null || gitCommitSha.isBlank()) {
            return Map.of("ok", false, "error", "git_commit_sha_required");
        }
        String cv = configVersion == null || configVersion.isBlank()
                ? "cfg-" + gitCommitSha.trim().substring(0, Math.min(8, gitCommitSha.trim().length()))
                : configVersion.trim();
        long ver = ++globalVersion;
        ConfigCell cell = new ConfigCell(gridCell.trim(), kind == null ? "GENERIC" : kind,
                payload == null ? Map.of() : Map.copyOf(payload), ver, cv);
        staging.put(cell.gridCell(), cell);
        stagingGitSha = gitCommitSha.trim();
        stagingConfigVersion = cv;
        stagingReady = true;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("staged", true);
        body.put("redisKey", "Redis:config:staging");
        body.put("gridCell", cell.gridCell());
        body.put("configVersion", cv);
        body.put("gitCommitSha", stagingGitSha);
        body.put("published", false);
        return body;
    }

    /**
     * Admin 全服发布：仅新开启 SceneInstance 生效；旧实例继续用 snapshot_cache。
     */
    public Map<String, Object> publishStaging(long nowMs) {
        return publishStaging(null, nowMs);
    }

    /**
     * 灰度发布：携带 grayConditions；匹配玩家读 gray staging，否则读全局基线。
     */
    public Map<String, Object> publishStaging(GrayConditions grayConditions, long nowMs) {
        if (!stagingReady || staging.isEmpty()) {
            return Map.of("ok", false, "error", "staging_empty");
        }
        if (grayConditions != null && grayConditions.label() != null && !grayConditions.label().isBlank()) {
            String label = grayConditions.label().trim();
            grayStaging.put(label, new ConcurrentHashMap<>(staging));
            grayConditionsByLabel.put(label, grayConditions);
            grayPublishActive = true;
            activeGrayLabel = label;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("published", true);
            body.put("gray", true);
            body.put("grayLabel", label);
            body.put("configVersion", stagingConfigVersion);
            body.put("gitCommitSha", stagingGitSha);
            body.put("atMs", nowMs);
            body.put("note", "灰度发布：匹配 grayConditions 的玩家生效，其余读全局基线");
            return body;
        }
        cells.putAll(staging);
        currentConfigVersion = stagingConfigVersion;
        staging.clear();
        stagingReady = false;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("published", true);
        body.put("configVersion", currentConfigVersion);
        body.put("gitCommitSha", stagingGitSha);
        body.put("atMs", nowMs);
        body.put("note", "仅新开启场景实例生效；进行中实例读 snapshot_cache");
        return body;
    }

    /** 灰度熔断：仅清除灰度层，全局基线不受影响。 */
    public Map<String, Object> rollbackGray(String grayLabel) {
        if (grayLabel == null || grayLabel.isBlank()) {
            grayStaging.clear();
            grayConditionsByLabel.clear();
            grayPublishActive = false;
            activeGrayLabel = "";
            return Map.of("ok", true, "rolledBack", "all_gray");
        }
        String label = grayLabel.trim();
        grayStaging.remove(label);
        grayConditionsByLabel.remove(label);
        if (label.equals(activeGrayLabel)) {
            grayPublishActive = !grayStaging.isEmpty();
            activeGrayLabel = grayPublishActive ? grayStaging.keySet().iterator().next() : "";
        }
        return Map.of("ok", true, "rolledBack", label, "grayPublishActive", grayPublishActive);
    }

    /** 按玩家上下文解析 grid cell：优先灰度，失败回退全局。 */
    public Map<String, Object> resolveForPlayer(PlayerConfigContext ctx, String gridCell) {
        if (ctx != null && grayPublishActive) {
            for (Map.Entry<String, GrayConditions> entry : grayConditionsByLabel.entrySet()) {
                GrayConditions cond = entry.getValue();
                if (!ConfigGrayMatcher.matches(cond, ctx)) {
                    continue;
                }
                ConcurrentHashMap<String, ConfigCell> bucket = grayStaging.get(entry.getKey());
                if (bucket == null) {
                    continue;
                }
                ConfigCell cell = bucket.get(gridCell);
                if (cell != null) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("ok", true);
                    body.put("fromGray", true);
                    body.put("grayLabel", entry.getKey());
                    body.put("gridCell", cell.gridCell());
                    body.put("kind", cell.kind());
                    body.put("version", cell.version());
                    body.put("configVersion", cell.configVersion());
                    body.put("payload", cell.payload());
                    return body;
                }
            }
        }
        return getCell(gridCell);
    }

    /** 战斗/场景实例启动时快照当前配置版本。 */
    public Map<String, Object> snapshotForInstance(String sceneInstanceId, long nowMs) {
        if (sceneInstanceId == null || sceneInstanceId.isBlank()) {
            return Map.of("ok", false, "error", "scene_instance_required");
        }
        ConfigSnapshot snap = new ConfigSnapshot(
                currentConfigVersion, stagingGitSha.isBlank() ? currentConfigVersion : stagingGitSha,
                Map.copyOf(cells), nowMs);
        instanceSnapshots.put(sceneInstanceId.trim(), snap);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("sceneInstanceId", sceneInstanceId.trim());
        body.put("configVersion", snap.configVersion());
        body.put("gitCommitSha", snap.gitCommitSha());
        body.put("cellCount", snap.cells().size());
        body.put("snapshotCache", true);
        return body;
    }

    /** 进行中战斗结算读取 snapshot_cache，而非全局 current_config。 */
    public Map<String, Object> resolveForInstance(String sceneInstanceId, String gridCell) {
        ConfigSnapshot snap = instanceSnapshots.get(sceneInstanceId == null ? "" : sceneInstanceId.trim());
        if (snap == null) {
            return getCell(gridCell);
        }
        ConfigCell cell = snap.cells().get(gridCell);
        if (cell == null) {
            return Map.of("ok", false, "error", "cell_not_found_in_snapshot",
                    "configVersion", snap.configVersion());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("fromSnapshot", true);
        body.put("sceneInstanceId", sceneInstanceId);
        body.put("configVersion", snap.configVersion());
        body.put("gridCell", cell.gridCell());
        body.put("kind", cell.kind());
        body.put("version", cell.version());
        body.put("payload", cell.payload());
        return body;
    }

    /** 增量 Patch：仅刷新指定 cell 缓存。 */
    public Map<String, Object> reloadCells(List<String> gridCells, long nowMs) {
        List<String> refreshed = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (gridCells != null) {
            for (String c : gridCells) {
                if (c == null || c.isBlank()) {
                    continue;
                }
                String key = c.trim();
                if (cells.containsKey(key)) {
                    reloadLog.put(key, nowMs);
                    refreshed.add(key);
                } else {
                    missing.add(key);
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("refreshed", refreshed);
        body.put("missing", missing);
        body.put("reloadedAtMs", nowMs);
        body.put("configVersion", currentConfigVersion);
        body.put("hint", "仅刷新指定 grid_cell 缓存，无需重启 Scene");
        return body;
    }

    public Map<String, Object> getCell(String gridCell) {
        ConfigCell cell = cells.get(gridCell);
        if (cell == null) {
            return Map.of("ok", false, "error", "cell_not_found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("gridCell", cell.gridCell());
        body.put("kind", cell.kind());
        body.put("version", cell.version());
        body.put("configVersion", cell.configVersion());
        body.put("payload", cell.payload());
        body.put("lastReloadMs", reloadLog.getOrDefault(gridCell, 0L));
        return body;
    }

    public Map<String, Object> snapshot() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ConfigCell c : cells.values()) {
            list.add(Map.of(
                    "gridCell", c.gridCell(),
                    "kind", c.kind(),
                    "version", c.version(),
                    "configVersion", c.configVersion()));
        }
        return Map.of("ok", true, "globalVersion", globalVersion,
                "configVersion", currentConfigVersion,
                "stagingReady", stagingReady,
                "grayPublishActive", grayPublishActive,
                "activeGrayLabel", activeGrayLabel,
                "grayLabelCount", grayStaging.size(),
                "cells", list, "count", list.size());
    }

    public boolean grayPublishActive() {
        return grayPublishActive;
    }

    public String activeGrayLabel() {
        return activeGrayLabel;
    }

    public String currentConfigVersion() {
        return currentConfigVersion;
    }
}
