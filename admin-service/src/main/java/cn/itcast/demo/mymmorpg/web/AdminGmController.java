package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.client.SceneGmClient;
import cn.itcast.demo.mymmorpg.service.AdminPermissionService;
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
import java.util.Map;

/**
 * 实时 GM 指令管道：绕过游戏协议，直注入 scene-service 大世界权威。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/gm")
public class AdminGmController {

    public static final String PERM_GM = "ops:gm";

    private final SceneGmClient sceneGmClient;
    private final AdminPermissionService adminPermissionService;

    public AdminGmController(SceneGmClient sceneGmClient,
                             AdminPermissionService adminPermissionService) {
        this.sceneGmClient = sceneGmClient;
        this.adminPermissionService = adminPermissionService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> catalog(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId) {
        requirePermission(adminUserId);
        return ResponseEntity.ok(sceneGmClient.catalog());
    }

    @PostMapping("/exec")
    public ResponseEntity<Map<String, Object>> exec(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        requirePermission(adminUserId);
        Map<String, Object> req = new LinkedHashMap<>(body == null ? Map.of() : body);
        req.putIfAbsent("level", "ADMIN");
        Map<String, Object> result = sceneGmClient.exec(req);
        return Boolean.TRUE.equals(result.get("ok"))
                ? ResponseEntity.ok(result)
                : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/load/mixed")
    public ResponseEntity<Map<String, Object>> mixedLoad(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(defaultValue = "500") int players,
            @RequestParam(defaultValue = "1000") int gatherOps,
            @RequestParam(defaultValue = "500") int combatLocks,
            @RequestParam(defaultValue = "5000") int moveQueries) {
        requirePermission(adminUserId);
        return ResponseEntity.ok(sceneGmClient.mixedLoad(players, gatherOps, combatLocks, moveQueries));
    }

    private void requirePermission(Long adminUserId) {
        if (!adminPermissionService.hasPermission(adminUserId, PERM_GM)
                && !adminPermissionService.hasPermission(adminUserId, "ops:reload")) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + PERM_GM);
        }
    }
}
