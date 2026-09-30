package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ShopOpsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AdminShopOpsService {

    public static final String PERM_SHOP_REFUND = "ops:shop-refund";
    public static final String PERM_SHOP_FULFILL = "ops:shop-fulfill";

    private final AdminPermissionService adminPermissionService;
    private final AdminOperationLogService operationLogService;
    private final ShopOpsClient shopOpsClient;

    public AdminShopOpsService(AdminPermissionService adminPermissionService,
                               AdminOperationLogService operationLogService,
                               ShopOpsClient shopOpsClient) {
        this.adminPermissionService = adminPermissionService;
        this.operationLogService = operationLogService;
        this.shopOpsClient = shopOpsClient;
    }

    public Map<String, Object> refund(Long adminUserId, String orderId, String reason, boolean reclaimItems) {
        requirePermission(adminUserId, PERM_SHOP_REFUND);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason == null ? "" : reason);
        body.put("reclaimItems", reclaimItems);
        Map<String, Object> result = shopOpsClient.refund(orderId, body);
        operationLogService.record(adminUserId, "SHOP_REFUND", null,
                "orderId=" + orderId + ",ok=" + result.get("ok") + ",reason=" + reason);
        return result;
    }

    public Map<String, Object> fulfill(Long adminUserId, String orderId) {
        requirePermission(adminUserId, PERM_SHOP_FULFILL);
        Map<String, Object> result = shopOpsClient.fulfill(orderId);
        operationLogService.record(adminUserId, "SHOP_FULFILL", null,
                "orderId=" + orderId + ",ok=" + result.get("ok"));
        return result;
    }

    private void requirePermission(Long adminUserId, String permission) {
        if (!adminPermissionService.hasPermission(adminUserId, permission)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + permission);
        }
    }
}
