package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.entity.VersionTimeline;
import cn.itcast.demo.mymmorpg.security.AdminRbacPresets;
import cn.itcast.demo.mymmorpg.service.AdminPermissionService;
import cn.itcast.demo.mymmorpg.service.VersionTimelineScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin 版本时间线 CRUD 与内存 Timeline 查询。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ops/version-timeline")
public class AdminVersionTimelineController {

    private final VersionTimelineScheduler scheduler;
    private final AdminPermissionService adminPermissionService;

    public AdminVersionTimelineController(
            VersionTimelineScheduler scheduler,
            AdminPermissionService adminPermissionService) {
        this.scheduler = scheduler;
        this.adminPermissionService = adminPermissionService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId) {
        require(adminUserId, AdminRbacPresets.PERM_OPS_RELOAD);
        return Map.of(
                "ok", true,
                "db", scheduler.findAll(),
                "memory", scheduler.listMemoryTimeline());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody VersionTimeline body) {
        require(adminUserId, AdminRbacPresets.PERM_CONFIG_IMPORT);
        VersionTimeline saved = scheduler.save(body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("timeline", saved);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/reload")
    public Map<String, Object> reload(@RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId) {
        require(adminUserId, AdminRbacPresets.PERM_OPS_RELOAD);
        scheduler.reloadMemoryTimeline();
        return Map.of("ok", true, "memory", scheduler.listMemoryTimeline());
    }

    private void require(Long adminUserId, String perm) {
        if (!adminPermissionService.hasPermission(adminUserId, AdminRbacPresets.PERM_ALL)
                && !adminPermissionService.hasPermission(adminUserId, perm)) {
            throw new IllegalStateException("无权执行该操作：需要权限 " + perm);
        }
    }
}
