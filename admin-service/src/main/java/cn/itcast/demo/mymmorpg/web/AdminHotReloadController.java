package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AdminPermissionService;
import cn.itcast.demo.mymmorpg.service.ConfigPublishAuditService;
import cn.itcast.demo.mymmorpg.service.HotReloadCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin 热更与发布审计入口（对齐 MyLunarCore HotReloadController）。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ops")
public class AdminHotReloadController {

    public static final String PERM_OPS_RELOAD = "ops:reload";

    private final HotReloadCoordinator hotReloadCoordinator;
    private final ConfigPublishAuditService auditService;
    private final AdminPermissionService adminPermissionService;

    public AdminHotReloadController(HotReloadCoordinator hotReloadCoordinator,
                                    ConfigPublishAuditService auditService,
                                    AdminPermissionService adminPermissionService) {
        this.hotReloadCoordinator = hotReloadCoordinator;
        this.auditService = auditService;
        this.adminPermissionService = adminPermissionService;
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId) {
        requirePermission(adminUserId);
        HotReloadCoordinator.ReloadResult result = hotReloadCoordinator.reloadAllStaged("admin:" + adminUserId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", result.success());
        body.put("message", result.message());
        body.put("stages", result.stages());
        body.put("details", result.details());
        ConfigPublishAuditService.PublishRecord last = auditService.lastSuccessful();
        if (last != null) {
            body.put("publishVersion", last.version());
        }
        return result.success() ? ResponseEntity.ok(body) : ResponseEntity.badRequest().body(body);
    }

    @GetMapping("/publish-history")
    public ResponseEntity<Map<String, Object>> publishHistory(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(defaultValue = "20") int limit) {
        requirePermission(adminUserId);
        List<Map<String, Object>> items = auditService.recent(limit).stream()
                .map(auditService::toMap)
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "OK");
        body.put("items", items);
        ConfigPublishAuditService.PublishRecord last = auditService.lastSuccessful();
        if (last != null) {
            body.put("lastSuccessful", auditService.toMap(last));
        }
        return ResponseEntity.ok(body);
    }

    private void requirePermission(Long adminUserId) {
        if (!adminPermissionService.hasPermission(adminUserId, PERM_OPS_RELOAD)
                && !adminPermissionService.hasPermission(adminUserId, "import:activity")
                && !adminPermissionService.hasPermission(adminUserId, "import:manifest")
                && !adminPermissionService.hasPermission(adminUserId, "import:shop")) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + PERM_OPS_RELOAD);
        }
    }
}
