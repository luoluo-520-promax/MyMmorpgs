package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AdminShopOpsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/shop")
public class AdminShopOpsController {

    private final AdminShopOpsService adminShopOpsService;

    public AdminShopOpsController(AdminShopOpsService adminShopOpsService) {
        this.adminShopOpsService = adminShopOpsService;
    }

    @PostMapping("/orders/{orderId}/refund")
    public ResponseEntity<Map<String, Object>> refund(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @PathVariable String orderId,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? "" : String.valueOf(body.get("reason"));
        boolean reclaim = body != null && Boolean.TRUE.equals(body.get("reclaimItems"));
        return ResponseEntity.ok(adminShopOpsService.refund(adminUserId, orderId, reason, reclaim));
    }

    @PostMapping("/orders/{orderId}/fulfill")
    public ResponseEntity<Map<String, Object>> fulfill(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @PathVariable String orderId) {
        return ResponseEntity.ok(adminShopOpsService.fulfill(adminUserId, orderId));
    }
}
