package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AdminAiBattleService;
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
@RequestMapping("/admin/ai/battle")
public class AdminAiBattleController {

    private final AdminAiBattleService adminAiBattleService;

    public AdminAiBattleController(AdminAiBattleService adminAiBattleService) {
        this.adminAiBattleService = adminAiBattleService;
    }

    /**
     * POST /admin/ai/battle/weekly-report
     * body: { "focus": "最近一周战士胜率偏高" }（可选）
     */
    @PostMapping("/weekly-report")
    public ResponseEntity<Map<String, Object>> weeklyReport(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody(required = false) Map<String, Object> body) {
        String focus = body == null || body.get("focus") == null ? null : String.valueOf(body.get("focus"));
        return ResponseEntity.ok(adminAiBattleService.weeklyReport(adminUserId, focus));
    }
}
