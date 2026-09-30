package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AdminAiComplaintService;
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
@RequestMapping("/admin/ai/complaints")
public class AdminAiComplaintController {

    private final AdminAiComplaintService adminAiComplaintService;

    public AdminAiComplaintController(AdminAiComplaintService adminAiComplaintService) {
        this.adminAiComplaintService = adminAiComplaintService;
    }

    /**
     * POST /admin/ai/complaints/suggest
     * body: { "complaintId": 10 } 或 { "content": "..." }
     */
    @PostMapping("/suggest")
    public ResponseEntity<Map<String, Object>> suggest(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @RequestBody Map<String, Object> body) {
        Long complaintId = body == null ? null : toLong(body.get("complaintId"));
        String content = body == null || body.get("content") == null ? null : String.valueOf(body.get("content"));
        return ResponseEntity.ok(adminAiComplaintService.suggest(adminUserId, complaintId, content));
    }

    /** POST /admin/ai/complaints/batch-triage */
    @PostMapping("/batch-triage")
    public ResponseEntity<Map<String, Object>> batchTriage(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId) {
        return ResponseEntity.ok(adminAiComplaintService.batchTriage(adminUserId));
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return Long.parseLong(s);
    }
}
