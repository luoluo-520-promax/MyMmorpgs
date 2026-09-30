package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Activity;
import cn.itcast.demo.mymmorpg.model.ActivityConfigPayload;
import cn.itcast.demo.mymmorpg.model.ActivityConditionPayload;
import cn.itcast.demo.mymmorpg.model.ActivityCostLimitPayload;
import cn.itcast.demo.mymmorpg.model.ActivityDisplayTextPayload;
import cn.itcast.demo.mymmorpg.model.ActivityImportDocument;
import cn.itcast.demo.mymmorpg.model.ActivityImportValidator;
import cn.itcast.demo.mymmorpg.model.ActivityShopProductPayload;
import cn.itcast.demo.mymmorpg.model.ActivityStagePayload;
import cn.itcast.demo.mymmorpg.model.ActivityTokenPayload;
import cn.itcast.demo.mymmorpg.model.ActivityUiResourcePayload;
import cn.itcast.demo.mymmorpg.model.RewardTierPayload;
import cn.itcast.demo.mymmorpg.model.admin.ActivityImportDryRunResult;
import cn.itcast.demo.mymmorpg.repository.ActivityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从 JSON 或 CSV（Excel 导出）导入活动配置并持久化到 activity 表。
 */
@Service
public class ActivityImportService {

    private static final TypeReference<List<ActivityImportDocument>> DOC_LIST =
            new TypeReference<>() {};

    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;

    public ActivityImportService(ActivityRepository activityRepository, ObjectMapper objectMapper) {
        this.activityRepository = activityRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Activity> importFromJson(String json) throws IOException {
        List<ActivityImportDocument> docs = parseJsonDocuments(json);
        requireValid(docs);
        List<Activity> saved = new ArrayList<>();
        for (ActivityImportDocument doc : docs) {
            saved.add(saveDocument(doc));
        }
        return saved;
    }

    @Transactional
    public List<Activity> importFromCsv(String csv) throws IOException {
        List<Map<String, String>> rows = parseCsvRows(csv);
        List<ActivityImportDocument> docs = new ArrayList<>();
        for (Map<String, String> row : rows) {
            docs.add(mapCsvRow(row));
        }
        requireValid(docs);
        List<Activity> saved = new ArrayList<>();
        for (ActivityImportDocument doc : docs) {
            saved.add(saveDocument(doc));
        }
        return saved;
    }

    /**
     * 解析并校验 JSON，不落库；用于 AI draft / 导入前预检。
     */
    public ActivityImportDryRunResult dryRunFromJson(String json) throws IOException {
        List<ActivityImportDocument> docs = parseJsonDocuments(json);
        ActivityImportValidator.ValidationOutcome outcome = ActivityImportValidator.validate(docs);
        if (!outcome.isValid()) {
            return ActivityImportDryRunResult.failed(outcome.errors(), outcome.warnings());
        }
        List<Long> upsertIds = new ArrayList<>();
        for (ActivityImportDocument doc : docs) {
            if (doc.id != null) {
                upsertIds.add(doc.id);
            }
        }
        return ActivityImportDryRunResult.ok(docs.size(), upsertIds, outcome.warnings());
    }

    public List<ActivityImportDocument> parseJsonDocuments(String json) throws IOException {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.startsWith("[")) {
            return objectMapper.readValue(trimmed, DOC_LIST);
        }
        return List.of(objectMapper.readValue(trimmed, ActivityImportDocument.class));
    }

    private static void requireValid(List<ActivityImportDocument> docs) {
        ActivityImportValidator.ValidationOutcome outcome = ActivityImportValidator.validate(docs);
        if (!outcome.isValid()) {
            throw new IllegalArgumentException(String.join("; ", outcome.errors()));
        }
    }

    ActivityImportDocument mapCsvRow(Map<String, String> row) throws IOException {
        ActivityImportDocument doc = new ActivityImportDocument();
        doc.id = parseLong(row.get("id"));
        doc.type = parseInt(row.get("type"));
        doc.opened = parseBoolean(row.get("opened"), true);
        doc.startTime = parseLong(row.get("startTime"));
        doc.endTime = parseLong(row.get("endTime"));
        doc.name = row.getOrDefault("name", "活动");
        doc.briefDesc = row.getOrDefault("briefDesc", "");
        doc.description = row.getOrDefault("description", "");
        doc.gameplay = row.getOrDefault("gameplay", "");
        doc.rules = row.getOrDefault("rules", "");
        doc.configVersion = parseLong(row.get("configVersion"));
        doc.rewardMethod = parseInt(row.get("rewardMethod"));
        doc.shopId = parseLong(row.get("shopId"));
        doc.conditions = readList(row.get("conditionsJson"), ActivityConditionPayload.class);
        doc.stages = readList(row.get("stagesJson"), ActivityStagePayload.class);
        doc.costLimit = readObject(row.get("costLimitJson"), ActivityCostLimitPayload.class, doc.costLimit);
        doc.rewardTiers = readList(row.get("rewardTiersJson"), RewardTierPayload.class);
        doc.token = readObject(row.get("tokenJson"), ActivityTokenPayload.class, doc.token);
        doc.shopProducts = readList(row.get("shopProductsJson"), ActivityShopProductPayload.class);
        doc.uiResources = readObject(row.get("uiResourcesJson"), ActivityUiResourcePayload.class, doc.uiResources);
        doc.displayText = readObject(row.get("displayTextJson"), ActivityDisplayTextPayload.class, doc.displayText);
        return doc;
    }

    private Activity saveDocument(ActivityImportDocument doc) throws IOException {
        Activity activity;
        if (doc.id != null) {
            Optional<Activity> existing = activityRepository.findById(doc.id);
            activity = existing.orElseGet(Activity::new);
            activity.setId(doc.id);
        } else {
            activity = new Activity();
        }
        activity.setType(doc.type);
        activity.setOpened(doc.opened != null ? doc.opened : true);
        activity.setData(objectMapper.writeValueAsString(toConfigPayload(doc)));
        return activityRepository.save(activity);
    }

    private ActivityConfigPayload toConfigPayload(ActivityImportDocument doc) {
        ActivityConfigPayload cfg = new ActivityConfigPayload();
        cfg.startTime = doc.startTime;
        cfg.endTime = doc.endTime;
        cfg.name = doc.name;
        cfg.briefDesc = doc.briefDesc;
        cfg.description = doc.description;
        cfg.gameplay = doc.gameplay;
        cfg.rules = doc.rules;
        cfg.configVersion = doc.configVersion;
        cfg.conditions = doc.conditions;
        cfg.stages = doc.stages;
        cfg.costLimit = doc.costLimit;
        cfg.rewardTiers = doc.rewardTiers;
        cfg.rewardMethod = doc.rewardMethod;
        cfg.token = doc.token;
        cfg.shopId = doc.shopId;
        cfg.shopProducts = doc.shopProducts;
        cfg.uiResources = doc.uiResources;
        cfg.displayText = doc.displayText;
        return cfg;
    }

    private List<Map<String, String>> parseCsvRows(String csv) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            String[] headers = splitCsvLine(headerLine);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("HEADER,")) {
                    continue;
                }
                String[] values = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private String[] splitCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(String[]::new);
    }

    private <T> List<T> readList(String json, Class<T> elementType) throws IOException {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    private <T> T readObject(String json, Class<T> type, T defaultValue) throws IOException {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        return objectMapper.readValue(json, type);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value) || "1".equals(value);
    }
}
