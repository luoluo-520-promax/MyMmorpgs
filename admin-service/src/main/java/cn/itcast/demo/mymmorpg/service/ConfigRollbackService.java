package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 热更配置快照与回滚：记录每次发布前后内容，支持按版本回滚与灰度比例。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class ConfigRollbackService {

    public record ConfigSnapshot(
            String versionId,
            String domain,
            String operator,
            String beforeJson,
            String afterJson,
            long createdAtMs,
            boolean gray,
            int grayPercent,
            String grayServerIds) {
    }

    public record RollbackRecord(
            String rollbackId,
            String fromVersionId,
            String toVersionId,
            String domain,
            String operator,
            long atMs,
            String reason) {
    }

    private final ConcurrentHashMap<String, ConfigSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> currentVersionByDomain = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<RollbackRecord> history = new CopyOnWriteArrayList<>();

    public ConfigSnapshot recordPublish(String domain, String operator, String beforeJson, String afterJson,
                                        boolean gray, int grayPercent, String grayServerIds) {
        String versionId = "cfg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ConfigSnapshot snap = new ConfigSnapshot(
                versionId,
                domain == null ? "unknown" : domain,
                operator == null ? "system" : operator,
                beforeJson == null ? "" : beforeJson,
                afterJson == null ? "" : afterJson,
                System.currentTimeMillis(),
                gray,
                Math.max(0, Math.min(100, grayPercent)),
                grayServerIds == null ? "" : grayServerIds);
        snapshots.put(versionId, snap);
        currentVersionByDomain.put(snap.domain(), versionId);
        return snap;
    }

    public Map<String, Object> rollback(String domain, String targetVersionId, String operator, String reason) {
        ConfigSnapshot target = snapshots.get(targetVersionId);
        if (target == null || (domain != null && !domain.equals(target.domain()))) {
            return Map.of("ok", false, "error", "version_not_found");
        }
        String from = currentVersionByDomain.getOrDefault(target.domain(), "");
        currentVersionByDomain.put(target.domain(), target.versionId());
        RollbackRecord rec = new RollbackRecord(
                "rb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                from, target.versionId(), target.domain(),
                operator == null ? "system" : operator,
                System.currentTimeMillis(),
                reason == null ? "" : reason);
        history.add(0, rec);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("rollbackId", rec.rollbackId());
        out.put("domain", target.domain());
        out.put("restoredVersionId", target.versionId());
        out.put("content", target.afterJson());
        return out;
    }

    public Map<String, Object> diff(String versionId) {
        ConfigSnapshot snap = snapshots.get(versionId);
        if (snap == null) {
            return Map.of("ok", false, "error", "version_not_found");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("versionId", snap.versionId());
        out.put("domain", snap.domain());
        out.put("before", snap.beforeJson());
        out.put("after", snap.afterJson());
        out.put("changed", !snap.beforeJson().equals(snap.afterJson()));
        out.put("gray", snap.gray());
        out.put("grayPercent", snap.grayPercent());
        out.put("grayServerIds", snap.grayServerIds());
        return out;
    }

    public List<Map<String, Object>> listSnapshots(String domain, int limit) {
        int lim = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> out = new ArrayList<>();
        snapshots.values().stream()
                .filter(s -> domain == null || domain.isBlank() || s.domain().equals(domain))
                .sorted((a, b) -> Long.compare(b.createdAtMs(), a.createdAtMs()))
                .limit(lim)
                .forEach(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("versionId", s.versionId());
                    m.put("domain", s.domain());
                    m.put("operator", s.operator());
                    m.put("createdAtMs", s.createdAtMs());
                    m.put("gray", s.gray());
                    m.put("grayPercent", s.grayPercent());
                    m.put("current", s.versionId().equals(currentVersionByDomain.get(s.domain())));
                    out.add(m);
                });
        return out;
    }

    public List<Map<String, Object>> rollbackHistory(int limit) {
        int lim = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < Math.min(lim, history.size()); i++) {
            RollbackRecord r = history.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rollbackId", r.rollbackId());
            m.put("fromVersionId", r.fromVersionId());
            m.put("toVersionId", r.toVersionId());
            m.put("domain", r.domain());
            m.put("operator", r.operator());
            m.put("atMs", r.atMs());
            m.put("reason", r.reason());
            out.add(m);
        }
        return out;
    }

    public String currentVersion(String domain) {
        return currentVersionByDomain.get(domain);
    }
}
