package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 聊天举报：记录举报单供运营审核，配合敏感词过滤与反骚扰积分形成闭环。
 */
@Service
public class ChatReportService {

    public enum ReportStatus { OPEN, REVIEWING, RESOLVED, REJECTED }

    public record Report(
            String reportId,
            long reporterId,
            long targetPlayerId,
            int channel,
            String contentSnippet,
            String reason,
            ReportStatus status,
            long createdAtMs) {
    }

    private final ConcurrentHashMap<String, Report> reports = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();
    private final ChatHarassmentService harassmentService;

    public ChatReportService(ChatHarassmentService harassmentService) {
        this.harassmentService = harassmentService;
    }

    public Map<String, Object> report(long reporterId, long targetPlayerId, int channel,
                                      String contentSnippet, String reason) {
        if (reporterId <= 0 || targetPlayerId <= 0 || reporterId == targetPlayerId) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        String id = "rpt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String reasonVal = reason == null ? "abuse" : reason;
        Report r = new Report(id, reporterId, targetPlayerId, channel,
                contentSnippet == null ? "" : contentSnippet.substring(0, Math.min(200, contentSnippet.length())),
                reasonVal,
                ReportStatus.OPEN, System.currentTimeMillis());
        reports.put(id, r);
        order.add(0, id);
        Map<String, Object> harass = harassmentService.recordReportHit(targetPlayerId, reasonVal);
        Map<String, Object> out = toView(r);
        out.put("ok", true);
        out.put("harassment", harass);
        return out;
    }

    public Map<String, Object> resolve(String reportId, boolean accept, String note) {
        Report cur = reports.get(reportId);
        if (cur == null) {
            return Map.of("ok", false, "error", "not_found");
        }
        Report next = new Report(cur.reportId(), cur.reporterId(), cur.targetPlayerId(), cur.channel(),
                cur.contentSnippet(), cur.reason() + (note == null || note.isBlank() ? "" : "|" + note),
                accept ? ReportStatus.RESOLVED : ReportStatus.REJECTED, cur.createdAtMs());
        reports.put(reportId, next);
        if (accept) {
            harassmentService.recordReportHit(cur.targetPlayerId(), cur.reason());
        }
        Map<String, Object> out = toView(next);
        out.put("ok", true);
        return out;
    }

    public List<Map<String, Object>> listOpen(int limit) {
        int lim = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (String id : order) {
            if (out.size() >= lim) {
                break;
            }
            Report r = reports.get(id);
            if (r != null && r.status() == ReportStatus.OPEN) {
                out.add(toView(r));
            }
        }
        return out;
    }

    private static Map<String, Object> toView(Report r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId", r.reportId());
        m.put("reporterId", r.reporterId());
        m.put("targetPlayerId", r.targetPlayerId());
        m.put("channel", r.channel());
        m.put("contentSnippet", r.contentSnippet());
        m.put("reason", r.reason());
        m.put("status", r.status().name());
        m.put("createdAtMs", r.createdAtMs());
        return m;
    }
}
