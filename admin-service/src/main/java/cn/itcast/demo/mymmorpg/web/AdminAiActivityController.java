package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AdminAiActivityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 活动策划 Copilot：自然语言生成草案 → dry-run → 二次确认导入。
 * <p>
 * 路径对齐现有 {@code /admin/**} 鉴权与网关路由（文档示意中的 /api/admin 前缀在此收敛为 /admin）。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ai/activity")
public class AdminAiActivityController {

    private final AdminAiActivityService adminAiActivityService;

    public AdminAiActivityController(AdminAiActivityService adminAiActivityService) {
        this.adminAiActivityService = adminAiActivityService;
    }

    /**
     * POST /admin/ai/activity/draft
     * body: { "prompt": "...", "template": "checkin|recharge|shop", "dryRun": true }
     */
    @PostMapping("/draft")
    public ResponseEntity<Map<String, Object>> draft(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        String prompt = body == null ? null : stringVal(body.get("prompt"));
        String template = body == null ? null : stringVal(body.get("template"));
        boolean dryRun = body == null || body.get("dryRun") == null || Boolean.TRUE.equals(body.get("dryRun"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("dryRun")));
        return ResponseEntity.ok(adminAiActivityService.draft(adminUserId, prompt, template, dryRun));
    }

    /**
     * POST /admin/ai/activity/apply
     * body: { "confirmToken": "..." }
     */
    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        String confirmToken = body == null ? null : stringVal(body.get("confirmToken"));
        return ResponseEntity.ok(adminAiActivityService.apply(adminUserId, confirmToken));
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
