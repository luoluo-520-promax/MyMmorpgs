package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ActivityImportClient;
import cn.itcast.demo.mymmorpg.client.QuestImportClient;
import cn.itcast.demo.mymmorpg.client.ShopImportClient;
import cn.itcast.demo.mymmorpg.client.UpdateImportClient;
import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AdminImportService {

    public static final String PERM_IMPORT_ACTIVITY = "import:activity";
    public static final String PERM_IMPORT_MANIFEST = "import:manifest";
    public static final String PERM_IMPORT_SHOP = "import:shop";
    public static final String PERM_IMPORT_QUEST = "import:quest";

    private final AdminPermissionService adminPermissionService;
    private final AdminOperationLogService operationLogService;
    private final ActivityImportClient activityImportClient;
    private final UpdateImportClient updateImportClient;
    private final ShopImportClient shopImportClient;
    private final QuestImportClient questImportClient;
    private final ConfigPublishAuditService publishAuditService;
    private final ConfigStagingValidationHook stagingValidationHook;
    private final ReferenceIntegrityValidator referenceIntegrityValidator;
    private final ObjectMapper objectMapper;

    public AdminImportService(AdminPermissionService adminPermissionService,
                              AdminOperationLogService operationLogService,
                              ActivityImportClient activityImportClient,
                              UpdateImportClient updateImportClient,
                              ShopImportClient shopImportClient,
                              QuestImportClient questImportClient,
                              ConfigPublishAuditService publishAuditService,
                              ConfigStagingValidationHook stagingValidationHook,
                              ReferenceIntegrityValidator referenceIntegrityValidator,
                              ObjectMapper objectMapper) {
        this.adminPermissionService = adminPermissionService;
        this.operationLogService = operationLogService;
        this.activityImportClient = activityImportClient;
        this.updateImportClient = updateImportClient;
        this.shopImportClient = shopImportClient;
        this.questImportClient = questImportClient;
        this.publishAuditService = publishAuditService;
        this.stagingValidationHook = stagingValidationHook;
        this.referenceIntegrityValidator = referenceIntegrityValidator;
        this.objectMapper = objectMapper;
    }

    public List<ImportResultItem> importActivitiesFromJson(Long adminUserId, String json) {
        requirePermission(adminUserId, PERM_IMPORT_ACTIVITY);
        Map<String, Object> staging = stagingValidationHook.validateBeforeApply("ACTIVITY", json);
        if (!Boolean.TRUE.equals(staging.get("ok"))) {
            throw new IllegalStateException("预发布校验失败: " + staging.get("errors"));
        }
        blockIfDependencyBroken(json, Set.of(), Set.of(), Set.of());
        List<ImportResultItem> items = activityImportClient.importJson(json);
        operationLogService.record(adminUserId, "IMPORT_ACTIVITY_JSON", null,
                "count=" + items.size() + ",ids=" + formatIds(items));
        publishAuditService.record("admin:" + adminUserId, "importActivitiesJson",
                List.of("ActivityConfigs"), false, true, "count=" + items.size());
        return items;
    }

    public List<ImportResultItem> importActivitiesFromCsv(Long adminUserId, String csv) {
        requirePermission(adminUserId, PERM_IMPORT_ACTIVITY);
        List<ImportResultItem> items = activityImportClient.importCsv(csv);
        operationLogService.record(adminUserId, "IMPORT_ACTIVITY_CSV", null,
                "count=" + items.size() + ",ids=" + formatIds(items));
        publishAuditService.record("admin:" + adminUserId, "importActivitiesCsv",
                List.of("ActivityConfigs.csv"), false, true, "count=" + items.size());
        return items;
    }

    public ImportResultItem importManifestFromJson(Long adminUserId, String json) {
        requirePermission(adminUserId, PERM_IMPORT_MANIFEST);
        ImportResultItem item = updateImportClient.importJson(json);
        operationLogService.record(adminUserId, "IMPORT_MANIFEST_JSON", item.getVersionNumber(),
                "versionCode=" + item.getVersionCode());
        publishAuditService.record("admin:" + adminUserId, "importManifestJson",
                List.of("version_manifest.json"), false, true,
                "versionCode=" + item.getVersionCode());
        return item;
    }

    public Map<String, Object> importShopProductsFromJson(Long adminUserId, String json, boolean dryRun) {
        requirePermission(adminUserId, PERM_IMPORT_SHOP);
        Map<String, Object> result = shopImportClient.importJson(dryRun, json);
        recordShopImport(adminUserId, dryRun, result, "JSON");
        return result;
    }

    public Map<String, Object> importShopProductsFromCsv(Long adminUserId, String csv, boolean dryRun) {
        requirePermission(adminUserId, PERM_IMPORT_SHOP);
        Map<String, Object> result = shopImportClient.importCsv(dryRun, csv);
        recordShopImport(adminUserId, dryRun, result, "CSV");
        return result;
    }

    public Map<String, Object> importQuestsFromJson(Long adminUserId, String json, boolean dryRun) {
        requirePermission(adminUserId, PERM_IMPORT_QUEST);
        Map<String, Object> result = questImportClient.importJson(dryRun, json);
        recordQuestImport(adminUserId, dryRun, result, "JSON");
        return result;
    }

    public Map<String, Object> importQuestsFromCsv(Long adminUserId, String csv, boolean dryRun) {
        requirePermission(adminUserId, PERM_IMPORT_QUEST);
        Map<String, Object> result = questImportClient.importCsv(dryRun, csv);
        recordQuestImport(adminUserId, dryRun, result, "CSV");
        return result;
    }

    /**
     * 依赖图校验：若调用方提供 known 目录则严格阻断；未提供时仅提取边不做硬拦（兼容旧调用）。
     * 当 JSON 中引用了明确缺失标记或 known 集合非空且有缺失时抛错。
     */
    @SuppressWarnings("unchecked")
    void blockIfDependencyBroken(
            String json,
            Set<String> knownItemIds,
            Set<String> knownQuestIds,
            Set<String> knownShopProductIds) {
        if (json == null || json.isBlank()) {
            return;
        }
        boolean hasCatalog = (knownItemIds != null && !knownItemIds.isEmpty())
                || (knownQuestIds != null && !knownQuestIds.isEmpty())
                || (knownShopProductIds != null && !knownShopProductIds.isEmpty());
        if (!hasCatalog) {
            return;
        }
        try {
            Object tree = objectMapper.readValue(json, Object.class);
            List<Map<String, Object>> refs = referenceIntegrityValidator.extractRefsFromJsonTree(tree, "$");
            Map<String, Object> result = referenceIntegrityValidator.validate(
                    refs,
                    knownItemIds == null ? Set.of() : knownItemIds,
                    knownQuestIds == null ? Set.of() : knownQuestIds,
                    knownShopProductIds == null ? Set.of() : knownShopProductIds);
            if (!Boolean.TRUE.equals(result.get("ok"))) {
                throw new IllegalStateException("依赖完整性校验失败: missingItemIds="
                        + result.get("missingItemIds")
                        + ", missingQuestIds=" + result.get("missingQuestIds")
                        + ", missingShopProductIds=" + result.get("missingShopProductIds")
                        + ", issues=" + result.get("issues"));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("依赖图解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 带已知目录的活动导入（CLI/Admin 上传时传入道具/任务 ID 集合）。
     */
    public List<ImportResultItem> importActivitiesFromJson(
            Long adminUserId,
            String json,
            Set<String> knownItemIds,
            Set<String> knownQuestIds,
            Set<String> knownShopProductIds) {
        requirePermission(adminUserId, PERM_IMPORT_ACTIVITY);
        Map<String, Object> staging = stagingValidationHook.validateBeforeApply("ACTIVITY", json);
        if (!Boolean.TRUE.equals(staging.get("ok"))) {
            throw new IllegalStateException("预发布校验失败: " + staging.get("errors"));
        }
        blockIfDependencyBroken(json, knownItemIds, knownQuestIds, knownShopProductIds);
        List<ImportResultItem> items = activityImportClient.importJson(json);
        operationLogService.record(adminUserId, "IMPORT_ACTIVITY_JSON", null,
                "count=" + items.size() + ",ids=" + formatIds(items));
        publishAuditService.record("admin:" + adminUserId, "importActivitiesJson",
                List.of("ActivityConfigs"), false, true, "count=" + items.size());
        return items;
    }

    private void recordShopImport(Long adminUserId, boolean dryRun, Map<String, Object> result, String format) {
        operationLogService.record(adminUserId,
                dryRun ? "IMPORT_SHOP_" + format + "_DRY_RUN" : "IMPORT_SHOP_" + format + "_APPLY", null,
                "ok=" + result.get("ok") + ",count=" + result.get("count"));
        publishAuditService.record("admin:" + adminUserId,
                dryRun ? "importShop" + format + "DryRun" : "importShop" + format + "Apply",
                List.of("shop/products." + format.toLowerCase()), false, Boolean.TRUE.equals(result.get("ok")),
                String.valueOf(result.get("count")));
        if (!dryRun && Boolean.TRUE.equals(result.get("ok"))) {
            shopImportClient.reload();
        }
    }

    private void recordQuestImport(Long adminUserId, boolean dryRun, Map<String, Object> result, String format) {
        operationLogService.record(adminUserId,
                dryRun ? "IMPORT_QUEST_" + format + "_DRY_RUN" : "IMPORT_QUEST_" + format + "_APPLY", null,
                "ok=" + result.get("ok") + ",count=" + result.get("count"));
        publishAuditService.record("admin:" + adminUserId,
                dryRun ? "importQuest" + format + "DryRun" : "importQuest" + format + "Apply",
                List.of("quest/Quests." + format.toLowerCase()), false, Boolean.TRUE.equals(result.get("ok")),
                String.valueOf(result.get("count")));
    }

    private void requirePermission(Long adminUserId, String permission) {
        if (!adminPermissionService.hasPermission(adminUserId, permission)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + permission);
        }
    }

    private static String formatIds(List<ImportResultItem> items) {
        return items.stream()
                .map(ImportResultItem::getId)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
