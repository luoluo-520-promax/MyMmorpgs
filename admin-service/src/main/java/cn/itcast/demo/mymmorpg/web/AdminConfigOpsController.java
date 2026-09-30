package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.security.AdminRbacPresets;
import cn.itcast.demo.mymmorpg.service.AdminPermissionService;
import cn.itcast.demo.mymmorpg.service.ConfigDiffService;
import cn.itcast.demo.mymmorpg.service.ConfigDraftPublishService;
import cn.itcast.demo.mymmorpg.service.ConfigRollbackService;
import cn.itcast.demo.mymmorpg.service.HotReloadCoordinator;
import cn.itcast.demo.mymmorpg.service.ReferenceIntegrityValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置回滚 / Diff / 引用完整性 / 灰度发布。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ops/config")
public class AdminConfigOpsController {

    private final ConfigRollbackService rollbackService;
    private final ConfigDiffService diffService;
    private final ReferenceIntegrityValidator integrityValidator;
    private final HotReloadCoordinator hotReloadCoordinator;
    private final AdminPermissionService adminPermissionService;
    private final ConfigDraftPublishService draftPublishService;

    public AdminConfigOpsController(ConfigRollbackService rollbackService,
                                    ConfigDiffService diffService,
                                    ReferenceIntegrityValidator integrityValidator,
                                    HotReloadCoordinator hotReloadCoordinator,
                                    AdminPermissionService adminPermissionService,
                                    ConfigDraftPublishService draftPublishService) {
        this.rollbackService = rollbackService;
        this.diffService = diffService;
        this.integrityValidator = integrityValidator;
        this.hotReloadCoordinator = hotReloadCoordinator;
        this.adminPermissionService = adminPermissionService;
        this.draftPublishService = draftPublishService;
    }

    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam String domain,
            @RequestParam(defaultValue = "false") boolean gray,
            @RequestParam(defaultValue = "100") int grayPercent,
            @RequestParam(required = false) String grayServerIds,
            @RequestBody Map<String, String> body) {
        requireAny(adminUserId, AdminRbacPresets.PERM_OPS_RELOAD, AdminRbacPresets.PERM_CONFIG_IMPORT,
                AdminRbacPresets.PERM_OPS_GRAY);
        String before = body == null ? "" : body.getOrDefault("before", "");
        String after = body == null ? "" : body.getOrDefault("after", "");
        ConfigRollbackService.ConfigSnapshot snap = rollbackService.recordPublish(
                domain, "admin:" + adminUserId, before, after, gray, grayPercent, grayServerIds);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("versionId", snap.versionId());
        out.put("domain", snap.domain());
        out.put("gray", snap.gray());
        out.put("grayPercent", snap.grayPercent());
        out.put("diff", diffService.diffJson(before, after));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/rollback")
    public ResponseEntity<Map<String, Object>> rollback(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam String domain,
            @RequestParam String versionId,
            @RequestParam(required = false) String reason) {
        requireAny(adminUserId, AdminRbacPresets.PERM_OPS_ROLLBACK, AdminRbacPresets.PERM_ALL);
        Map<String, Object> result = rollbackService.rollback(domain, versionId, "admin:" + adminUserId, reason);
        if (!Boolean.TRUE.equals(result.get("ok"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/diff")
    public Map<String, Object> diff(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam String versionId) {
        requireAny(adminUserId, AdminRbacPresets.PERM_CONFIG_PREVIEW, AdminRbacPresets.PERM_OPS_RELOAD);
        return rollbackService.diff(versionId);
    }

    @GetMapping("/snapshots")
    public Map<String, Object> snapshots(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "20") int limit) {
        requireAny(adminUserId, AdminRbacPresets.PERM_OPS_RELOAD, AdminRbacPresets.PERM_CONFIG_PREVIEW);
        return Map.of("ok", true, "items", rollbackService.listSnapshots(domain, limit));
    }

    @GetMapping("/rollback-history")
    public Map<String, Object> rollbackHistory(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(defaultValue = "20") int limit) {
        requireAny(adminUserId, AdminRbacPresets.PERM_OPS_ROLLBACK, AdminRbacPresets.PERM_OPS_RELOAD);
        return Map.of("ok", true, "items", rollbackService.rollbackHistory(limit));
    }

    @PostMapping("/validate-refs")
    public Map<String, Object> validateRefs(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        requireAny(adminUserId, AdminRbacPresets.PERM_CONFIG_IMPORT, AdminRbacPresets.PERM_CONFIG_PREVIEW);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refs = body == null ? List.of()
                : (List<Map<String, Object>>) body.getOrDefault("refs", List.of());
        @SuppressWarnings("unchecked")
        Set<String> items = body == null ? Set.of()
                : Set.copyOf((List<String>) body.getOrDefault("knownItemIds", List.of()));
        @SuppressWarnings("unchecked")
        Set<String> quests = body == null ? Set.of()
                : Set.copyOf((List<String>) body.getOrDefault("knownQuestIds", List.of()));
        @SuppressWarnings("unchecked")
        Set<String> products = body == null ? Set.of()
                : Set.copyOf((List<String>) body.getOrDefault("knownShopProductIds", List.of()));
        return integrityValidator.validate(refs, items, quests, products);
    }

    @PostMapping("/gray-reload")
    public ResponseEntity<Map<String, Object>> grayReload(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(defaultValue = "10") int grayPercent,
            @RequestParam(required = false) String grayServerIds) {
        requireAny(adminUserId, AdminRbacPresets.PERM_OPS_GRAY, AdminRbacPresets.PERM_OPS_RELOAD);
        HotReloadCoordinator.ReloadResult result = hotReloadCoordinator.reloadAllStaged("admin-gray:" + adminUserId);
        rollbackService.recordPublish("hot-reload", "admin:" + adminUserId,
                "", "{\"stages\":" + result.stages() + "}", true, grayPercent, grayServerIds);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", result.success());
        body.put("message", result.message());
        body.put("stages", result.stages());
        body.put("grayPercent", grayPercent);
        body.put("grayServerIds", grayServerIds == null ? "" : grayServerIds);
        return result.success() ? ResponseEntity.ok(body) : ResponseEntity.badRequest().body(body);
    }

    @PostMapping("/draft")
    public ResponseEntity<Map<String, Object>> saveDraft(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam String domain,
            @RequestBody String draftJson) {
        requireAny(adminUserId, AdminRbacPresets.PERM_CONFIG_IMPORT, AdminRbacPresets.PERM_CONFIG_EDIT,
                AdminRbacPresets.PERM_ALL);
        ConfigDraftPublishService.Domain d = ConfigDraftPublishService.Domain.valueOf(domain.trim().toUpperCase());
        return ResponseEntity.ok(draftPublishService.saveDraft(d, draftJson));
    }

    @PostMapping("/draft/publish-all")
    public ResponseEntity<Map<String, Object>> publishAllDrafts(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId) {
        requireAny(adminUserId, AdminRbacPresets.PERM_CONFIG_IMPORT, AdminRbacPresets.PERM_CONFIG_EDIT,
                AdminRbacPresets.PERM_ALL);
        return ResponseEntity.ok(draftPublishService.publishAll());
    }

    private void requireAny(Long adminUserId, String... codes) {
        if (adminPermissionService.hasPermission(adminUserId, AdminRbacPresets.PERM_ALL)) {
            return;
        }
        for (String code : codes) {
            if (adminPermissionService.hasPermission(adminUserId, code)) {
                return;
            }
        }
        throw new IllegalStateException("无权执行该操作：需要权限 " + String.join("|", codes));
    }
}
