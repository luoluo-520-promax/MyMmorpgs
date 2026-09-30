package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AdminAiShopService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 氪金礼包 Copilot：shop_pack / topup / discount / first_charge 草案。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ai/shop")
public class AdminAiShopController {

    private final AdminAiShopService adminAiShopService;

    public AdminAiShopController(AdminAiShopService adminAiShopService) {
        this.adminAiShopService = adminAiShopService;
    }

    @PostMapping("/draft")
    public ResponseEntity<Map<String, Object>> draft(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        String prompt = body == null ? null : stringVal(body.get("prompt"));
        String template = body == null ? null : stringVal(body.get("template"));
        boolean dryRun = body == null || body.get("dryRun") == null || Boolean.TRUE.equals(body.get("dryRun"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("dryRun")));
        return ResponseEntity.ok(adminAiShopService.draft(adminUserId, prompt, template, dryRun));
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        String confirmToken = body == null ? null : stringVal(body.get("confirmToken"));
        return ResponseEntity.ok(adminAiShopService.apply(adminUserId, confirmToken));
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
