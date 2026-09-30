package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AdminAiQuestService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/ai/quest")
public class AdminAiQuestController {

    private final AdminAiQuestService adminAiQuestService;

    public AdminAiQuestController(AdminAiQuestService adminAiQuestService) {
        this.adminAiQuestService = adminAiQuestService;
    }

    /** POST /admin/ai/quest/draft  body: { "prompt": "...", "dryRun": true } */
    @PostMapping("/draft")
    public ResponseEntity<Map<String, Object>> draft(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        String prompt = body == null || body.get("prompt") == null ? null : String.valueOf(body.get("prompt"));
        boolean dryRun = body == null || body.get("dryRun") == null || Boolean.TRUE.equals(body.get("dryRun"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("dryRun")));
        return ResponseEntity.ok(adminAiQuestService.draft(adminUserId, prompt, dryRun));
    }
}
