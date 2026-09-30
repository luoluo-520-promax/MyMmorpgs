package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ActivityImportClient;
import cn.itcast.demo.mymmorpg.model.admin.ActivityImportDryRunResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置入库前 staging 校验钩子：调用 activity dry-run（含日程冲突检测）。
 */
@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class ConfigStagingValidationHook {

    private final ObjectProvider<ActivityImportClient> activityImportClient;

    public ConfigStagingValidationHook(ObjectProvider<ActivityImportClient> activityImportClient) {
        this.activityImportClient = activityImportClient;
    }

    /**
     * 在 apply 前执行 dry-run；失败时返回 ok=false 与错误列表。
     */
    public Map<String, Object> validateBeforeApply(String domain, String payloadJson) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (domain == null || domain.isBlank()) {
            out.put("ok", false);
            out.put("errors", List.of("domain_required"));
            return out;
        }
        String d = domain.trim().toUpperCase();
        if (!"ACTIVITY".equals(d) && !"ACTIVITIES".equals(d)) {
            out.put("ok", true);
            out.put("skipped", true);
            out.put("reason", "no_staging_validator_for_domain");
            return out;
        }
        ActivityImportClient client = activityImportClient.getIfAvailable();
        if (client == null) {
            out.put("ok", false);
            out.put("errors", List.of("activity_import_client_unavailable"));
            return out;
        }
        try {
            ActivityImportDryRunResult result = client.dryRunJson(payloadJson == null ? "[]" : payloadJson);
            List<String> errors = result == null || result.getErrors() == null
                    ? List.of() : new ArrayList<>(result.getErrors());
            List<String> warnings = result == null || result.getWarnings() == null
                    ? List.of() : new ArrayList<>(result.getWarnings());
            boolean ok = result != null && result.isValid() && errors.isEmpty();
            out.put("ok", ok);
            out.put("errors", errors);
            out.put("warnings", warnings);
            if (result != null) {
                out.put("documentCount", result.getDocumentCount());
            }
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("errors", List.of("staging_dry_run_failed:" + e.getMessage()));
            return out;
        }
    }
}
