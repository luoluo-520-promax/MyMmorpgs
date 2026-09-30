package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.entity.Complaint;
import cn.itcast.demo.mymmorpg.service.AdminComplaintService;
import cn.itcast.demo.mymmorpg.web.AdminRequestAttributes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@RequestMapping("/admin/complaints")
public class AdminComplaintController {

    private final AdminComplaintService adminComplaintService;

    public AdminComplaintController(AdminComplaintService adminComplaintService) {
        this.adminComplaintService = adminComplaintService;
    }

    @GetMapping
    public List<Complaint> listPending() {
        return adminComplaintService.listPendingComplaints();
    }

    @PostMapping("/{complaintId}/handle")
    public ResponseEntity<Map<String, Object>> handle(
            @RequestAttribute(AdminRequestAttributes.ADMIN_USER_ID) Long adminUserId,
            @PathVariable Long complaintId,
            @RequestBody Map<String, String> body) {
        String action = body == null ? "RESOLVE" : body.getOrDefault("action", "RESOLVE");
        Complaint updated = adminComplaintService.handleComplaint(adminUserId, complaintId, action);
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "complaintId", updated.getId(),
                "newStatus", updated.getStatus().name()));
    }
}
