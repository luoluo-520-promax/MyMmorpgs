package cn.itcast.demo.mymmorpg.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动 / 商城配置草稿与一键发布。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class ConfigDraftPublishService {

    public enum Domain { ACTIVITY, SHOP }

    private final ConcurrentHashMap<String, String> drafts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> published = new ConcurrentHashMap<>();

    public Map<String, Object> saveDraft(Domain domain, String draftJson) {
        if (domain == null) {
            return Map.of("ok", false, "error", "domain_required");
        }
        String body = draftJson == null ? "" : draftJson;
        drafts.put(domain.name(), body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("domain", domain.name());
        out.put("publishStatus", "DRAFT");
        out.put("length", body.length());
        return out;
    }

    public Map<String, Object> publishAll() {
        Map<String, Object> out = new LinkedHashMap<>();
        int count = 0;
        for (Domain d : Domain.values()) {
            String draft = drafts.get(d.name());
            if (draft == null) {
                continue;
            }
            published.put(d.name(), draft);
            drafts.remove(d.name());
            count++;
        }
        out.put("ok", true);
        out.put("publishedCount", count);
        out.put("publishStatus", "PUBLISHED");
        out.put("domains", published.keySet());
        return out;
    }

    public Map<String, Object> publish(Domain domain) {
        if (domain == null) {
            return Map.of("ok", false, "error", "domain_required");
        }
        String draft = drafts.get(domain.name());
        if (draft == null) {
            return Map.of("ok", false, "error", "no_draft");
        }
        published.put(domain.name(), draft);
        drafts.remove(domain.name());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("domain", domain.name());
        out.put("publishStatus", "PUBLISHED");
        return out;
    }

    public String getDraft(Domain domain) {
        return domain == null ? null : drafts.get(domain.name());
    }

    public String getPublished(Domain domain) {
        return domain == null ? null : published.get(domain.name());
    }
}
