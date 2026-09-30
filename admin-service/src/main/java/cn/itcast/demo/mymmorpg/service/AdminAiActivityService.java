package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ActivityImportClient;
import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.model.admin.ActivityImportDryRunResult;
import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem;
import cn.itcast.demo.mymmorpg.service.ai.ActivityDraftTemplateGenerator;
import cn.itcast.demo.mymmorpg.service.ai.AiActivityDraftStore;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@EnableConfigurationProperties(AdminAiProperties.class)
public class AdminAiActivityService {

    public static final String PERM_AI_ACTIVITY = "import:activity";

    private final AdminPermissionService adminPermissionService;
    private final AdminOperationLogService operationLogService;
    private final ActivityImportClient activityImportClient;
    private final AdminAiProperties aiProperties;
    private final AiModelClient aiModelClient;
    private final AiActivityDraftStore draftStore;
    private final ActivityDraftTemplateGenerator templateGenerator;
    private final ObjectMapper objectMapper;
    private final AdminAiPlatformBridge aiPlatformBridge;

    public AdminAiActivityService(AdminPermissionService adminPermissionService,
                                  AdminOperationLogService operationLogService,
                                  ActivityImportClient activityImportClient,
                                  AdminAiProperties aiProperties,
                                  AiModelClient aiModelClient,
                                  AiActivityDraftStore draftStore,
                                  ObjectMapper objectMapper,
                                  AdminAiPlatformBridge aiPlatformBridge) {
        this.adminPermissionService = adminPermissionService;
        this.operationLogService = operationLogService;
        this.activityImportClient = activityImportClient;
        this.aiProperties = aiProperties;
        this.aiModelClient = aiModelClient;
        this.draftStore = draftStore;
        this.objectMapper = objectMapper;
        this.aiPlatformBridge = aiPlatformBridge;
        this.templateGenerator = new ActivityDraftTemplateGenerator(objectMapper);
    }

    public Map<String, Object> draft(Long adminUserId, String prompt, String template, boolean dryRun) {
        requirePermission(adminUserId);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String resolvedTemplate = templateGenerator.resolveTemplate(template, prompt);
        List<String> localWarnings = new ArrayList<>();
        String modelUsed = "template";
        ObjectNode document;

        Optional<String> llmJson = aiModelClient.generateActivityJson(resolvedTemplate, prompt);
        if (llmJson.isPresent()) {
            try {
                JsonNode parsed = objectMapper.readTree(llmJson.get());
                if (parsed instanceof ObjectNode obj) {
                    document = obj;
                    modelUsed = aiProperties.getModel();
                    if (document.has("_warnings") && document.get("_warnings").isArray()) {
                        for (JsonNode w : document.get("_warnings")) {
                            localWarnings.add(w.asText());
                        }
                        document.remove("_warnings");
                    }
                } else {
                    localWarnings.add("模型返回非对象 JSON，已回退本地模板");
                    document = templateGenerator.generate(resolvedTemplate, prompt);
                }
            } catch (Exception e) {
                localWarnings.add("模型输出解析失败，已回退本地模板");
                document = templateGenerator.generate(resolvedTemplate, prompt);
            }
        } else {
            if (aiProperties.isEnabled()) {
                localWarnings.add("外部模型不可用或未配置密钥，已使用本地模板生成");
            }
            document = templateGenerator.generate(resolvedTemplate, prompt);
        }

        String documentJson;
        try {
            documentJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
        } catch (Exception e) {
            throw new IllegalStateException("序列化活动草案失败", e);
        }
        String resultHash = sha256Hex(documentJson);

        ActivityImportDryRunResult dryRunResult = null;
        List<String> errors = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> draftMap = objectMapper.convertValue(document, Map.class);
        Map<String, Object> aiValidation = aiPlatformBridge.contentValidate(toContentValidationDraft(draftMap));
        if (Boolean.FALSE.equals(aiValidation.get("pass"))) {
            Object aiErrors = aiValidation.get("errors");
            if (aiErrors instanceof Iterable<?> iterable) {
                for (Object e : iterable) {
                    errors.add(String.valueOf(e));
                }
            }
        }
        if (dryRun) {
            dryRunResult = activityImportClient.dryRunJson(documentJson);
            if (dryRunResult.getWarnings() != null) {
                localWarnings.addAll(dryRunResult.getWarnings());
            }
            if (!dryRunResult.isValid() && dryRunResult.getErrors() != null) {
                errors.addAll(dryRunResult.getErrors());
            }
        }

        String confirmToken = null;
        if (errors.isEmpty()) {
            confirmToken = draftStore.put(adminUserId, documentJson, resultHash, aiProperties.getConfirmTtlMs());
        }

        operationLogService.record(adminUserId, "AI_ACTIVITY_DRAFT", null,
                truncate("trace=" + traceId + ",tpl=" + resolvedTemplate + ",model=" + modelUsed
                        + ",hash=" + resultHash.substring(0, 12) + ",ok=" + errors.isEmpty()));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", errors.isEmpty() ? "OK" : "INVALID");
        resp.put("traceId", traceId);
        resp.put("template", resolvedTemplate);
        resp.put("promptVersion", aiProperties.getPromptVersion());
        resp.put("model", modelUsed);
        resp.put("resultHash", resultHash);
        resp.put("document", document);
        resp.put("warnings", localWarnings);
        resp.put("errors", errors);
        resp.put("dryRunResult", dryRunResult);
        resp.put("aiValidation", aiValidation);
        resp.put("confirmToken", confirmToken);
        return resp;
    }

    public Map<String, Object> apply(Long adminUserId, String confirmToken) {
        requirePermission(adminUserId);
        if (confirmToken == null || confirmToken.isBlank()) {
            throw new IllegalArgumentException("confirmToken 不能为空：请先调用 draft 获取二次确认令牌");
        }
        AiActivityDraftStore.Entry entry = draftStore.consume(confirmToken, adminUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "confirmToken 无效或已过期，请重新生成草案"));

        List<ImportResultItem> items = activityImportClient.importJson(entry.documentJson());
        operationLogService.record(adminUserId, "AI_ACTIVITY_APPLY", null,
                truncate("hash=" + entry.resultHash().substring(0, Math.min(12, entry.resultHash().length()))
                        + ",count=" + items.size() + ",ids=" + formatIds(items)));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "OK");
        resp.put("resultHash", entry.resultHash());
        resp.put("count", items.size());
        resp.put("items", items);
        return resp;
    }

    private static Map<String, Object> toContentValidationDraft(Map<String, Object> importDoc) {
        Map<String, Object> view = new LinkedHashMap<>();
        String title = str(importDoc, "name");
        Object display = importDoc.get("displayText");
        if (display instanceof Map<?, ?> dm) {
            Object dt = dm.get("title");
            if (dt != null && !String.valueOf(dt).isBlank()) {
                title = String.valueOf(dt).trim();
            }
        }
        view.put("title", title);
        view.put("description", str(importDoc, "description"));
        Object type = importDoc.get("type");
        view.put("type", type == null ? "activity" : "activity_" + type);
        String id = str(importDoc, "id");
        if (id.isBlank()) {
            id = "act_" + Integer.toHexString((title + view.get("type")).hashCode() & 0x7fffffff);
        }
        view.put("id", id);
        view.put("rewardMul", 1.0);
        view.put("discount", 1.0);
        return view;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private void requirePermission(Long adminUserId) {
        if (!adminPermissionService.hasPermission(adminUserId, PERM_AI_ACTIVITY)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + PERM_AI_ACTIVITY);
        }
    }

    private static String formatIds(List<ImportResultItem> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(items.get(i).getId());
        }
        return sb.toString();
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return "";
        }
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("计算结果哈希失败", e);
        }
    }
}
