package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ShopImportClient;
import cn.itcast.demo.mymmorpg.service.ai.AiActivityDraftStore;
import cn.itcast.demo.mymmorpg.service.ai.ShopPackDraftTemplateGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 氪金礼包草稿：本地模板生成 → dry-run → 确认导入 shop-products。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AdminAiShopService {

    public static final String PERM_AI_SHOP = "import:shop";
    private static final long CONFIRM_TTL_MS = 30 * 60 * 1000L;

    private final AdminPermissionService adminPermissionService;
    private final AdminOperationLogService operationLogService;
    private final ShopImportClient shopImportClient;
    private final AiActivityDraftStore draftStore;
    private final ShopPackDraftTemplateGenerator templateGenerator;
    private final ObjectMapper objectMapper;

    public AdminAiShopService(AdminPermissionService adminPermissionService,
                              AdminOperationLogService operationLogService,
                              ShopImportClient shopImportClient,
                              AiActivityDraftStore draftStore,
                              ObjectMapper objectMapper) {
        this.adminPermissionService = adminPermissionService;
        this.operationLogService = operationLogService;
        this.shopImportClient = shopImportClient;
        this.draftStore = draftStore;
        this.objectMapper = objectMapper;
        this.templateGenerator = new ShopPackDraftTemplateGenerator(objectMapper);
    }

    public Map<String, Object> draft(Long adminUserId, String prompt, String template, boolean dryRunOnly) {
        requirePermission(adminUserId);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        String resolved = templateGenerator.resolveTemplate(template, prompt);
        ObjectNode document = templateGenerator.generate(resolved, prompt);
        String documentJson;
        try {
            documentJson = objectMapper.writeValueAsString(document);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 shop_pack 草案失败", e);
        }
        Map<String, Object> dryRunResult = shopImportClient.importJson(true, documentJson);
        String confirmToken = draftStore.put(adminUserId, documentJson, sha256(documentJson), CONFIRM_TTL_MS);

        operationLogService.record(adminUserId, "AI_SHOP_PACK_DRAFT", null,
                "template=" + resolved + ",ok=" + dryRunResult.get("ok"));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", Boolean.TRUE.equals(dryRunResult.get("ok")) ? "OK" : "FAIL");
        resp.put("template", resolved);
        resp.put("model", "template");
        resp.put("confirmToken", confirmToken);
        resp.put("document", document);
        resp.put("dryRun", dryRunResult);
        if (!dryRunOnly && Boolean.TRUE.equals(dryRunResult.get("ok"))) {
            resp.put("apply", apply(adminUserId, confirmToken));
        }
        return resp;
    }

    public Map<String, Object> apply(Long adminUserId, String confirmToken) {
        requirePermission(adminUserId);
        Optional<AiActivityDraftStore.Entry> entry = draftStore.consume(confirmToken, adminUserId);
        if (entry.isEmpty()) {
            throw new IllegalStateException("confirmToken 无效或已过期");
        }
        Map<String, Object> result = shopImportClient.importJson(false, entry.get().documentJson());
        if (Boolean.TRUE.equals(result.get("ok"))) {
            shopImportClient.reload();
        }
        operationLogService.record(adminUserId, "AI_SHOP_PACK_APPLY", null,
                "ok=" + result.get("ok") + ",count=" + result.get("count"));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", Boolean.TRUE.equals(result.get("ok")) ? "OK" : "FAIL");
        resp.put("item", result);
        return resp;
    }

    private void requirePermission(Long adminUserId) {
        if (!adminPermissionService.hasPermission(adminUserId, PERM_AI_SHOP)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + PERM_AI_SHOP);
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
