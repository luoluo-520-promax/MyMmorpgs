package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.activity.ActivitySnapshotManager;
import cn.itcast.demo.mymmorpg.security.AdminRbacPresets;
import cn.itcast.demo.mymmorpg.service.AdminPermissionService;
import cn.itcast.demo.mymmorpg.world.content.GrayConditions;
import cn.itcast.demo.mymmorpg.world.content.OpenWorldConfigPatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Admin 灰度发布熔断、活动快照与版本回退。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ops/version")
public class AdminVersionOpsController {

    private final OpenWorldConfigPatchService configPatchService;
    private final ActivitySnapshotManager snapshotManager;
    private final AdminPermissionService adminPermissionService;

    public AdminVersionOpsController(
            OpenWorldConfigPatchService configPatchService,
            ActivitySnapshotManager snapshotManager,
            AdminPermissionService adminPermissionService) {
        this.configPatchService = configPatchService;
        this.snapshotManager = snapshotManager;
        this.adminPermissionService = adminPermissionService;
    }

    @PostMapping("/gray-publish")
    public Map<String, Object> grayPublish(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> grayConditions) {
        require(adminUserId, AdminRbacPresets.PERM_OPS_GRAY);
        GrayConditions cond = GrayConditions.fromMap(grayConditions);
        Map<String, Object> out = configPatchService.publishStaging(cond, System.currentTimeMillis());
        out.put("grayConditions", grayConditions);
        return out;
    }

    @PostMapping("/gray-rollback")
    public Map<String, Object> grayRollback(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam(required = false) String grayLabel) {
        require(adminUserId, AdminRbacPresets.PERM_OPS_GRAY);
        return configPatchService.rollbackGray(grayLabel);
    }

    @PostMapping("/snapshot-players")
    public Map<String, Object> snapshotPlayers(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam String versionCode) {
        require(adminUserId, AdminRbacPresets.PERM_OPS_RELOAD);
        return snapshotManager.snapshotActivePlayers(versionCode);
    }

    @PostMapping("/rollback-player-data")
    public Map<String, Object> rollbackPlayerData(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestParam long playerId,
            @RequestParam String versionCode) {
        require(adminUserId, AdminRbacPresets.PERM_OPS_ROLLBACK);
        return snapshotManager.rollbackPlayerData(playerId, versionCode);
    }

    @GetMapping("/gray-status")
    public Map<String, Object> grayStatus(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId) {
        require(adminUserId, AdminRbacPresets.PERM_OPS_RELOAD);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("grayPublishActive", configPatchService.grayPublishActive());
        out.put("activeGrayLabel", configPatchService.activeGrayLabel());
        out.put("configSnapshot", configPatchService.snapshot());
        return out;
    }

    private void require(Long adminUserId, String perm) {
        if (!adminPermissionService.hasPermission(adminUserId, AdminRbacPresets.PERM_ALL)
                && !adminPermissionService.hasPermission(adminUserId, perm)) {
            throw new IllegalStateException("无权执行该操作：需要权限 " + perm);
        }
    }
}
