package cn.itcast.demo.mymmorpg.model.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * 活动导入 dry-run / 校验结果，供 admin AI 与 internal API 共用。
 */
public class ActivityImportDryRunResult {

    private boolean valid = true;
    private int documentCount;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<Long> upsertIds = new ArrayList<>();

    public static ActivityImportDryRunResult ok(int documentCount, List<Long> upsertIds, List<String> warnings) {
        ActivityImportDryRunResult r = new ActivityImportDryRunResult();
        r.valid = true;
        r.documentCount = documentCount;
        r.upsertIds = upsertIds == null ? new ArrayList<>() : new ArrayList<>(upsertIds);
        r.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        return r;
    }

    public static ActivityImportDryRunResult failed(List<String> errors, List<String> warnings) {
        ActivityImportDryRunResult r = new ActivityImportDryRunResult();
        r.valid = false;
        r.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
        r.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        r.documentCount = 0;
        return r;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<Long> getUpsertIds() {
        return upsertIds;
    }

    public void setUpsertIds(List<Long> upsertIds) {
        this.upsertIds = upsertIds;
    }
}
