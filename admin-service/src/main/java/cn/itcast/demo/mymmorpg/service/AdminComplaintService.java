package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Complaint;
import cn.itcast.demo.mymmorpg.repository.ComplaintRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
public class AdminComplaintService {

    public static final String PERM_COMPLAINT_HANDLE = "complaint:handle";

    private final AdminPermissionService adminPermissionService;
    private final ComplaintRepository complaintRepository;
    private final AdminOperationLogService operationLogService;

    public AdminComplaintService(AdminPermissionService adminPermissionService,
                                 ComplaintRepository complaintRepository,
                                 AdminOperationLogService operationLogService) {
        this.adminPermissionService = adminPermissionService;
        this.complaintRepository = complaintRepository;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public List<Complaint> listPendingComplaints() {
        return complaintRepository.findAll().stream()
                .filter(c -> c.getStatus() == Complaint.Status.PENDING)
                .toList();
    }

    @Transactional
    public Complaint handleComplaint(Long adminUserId, Long complaintId, String action) {
        if (!adminPermissionService.hasPermission(adminUserId, PERM_COMPLAINT_HANDLE)) {
            throw new IllegalStateException("无权执行该操作：缺少权限 " + PERM_COMPLAINT_HANDLE);
        }
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("投诉单不存在: " + complaintId));
        if (complaint.getStatus() != Complaint.Status.PENDING) {
            throw new IllegalStateException("投诉单已处理，当前状态: " + complaint.getStatus());
        }

        Complaint.Status newStatus = "REJECT".equalsIgnoreCase(action)
                ? Complaint.Status.REJECTED
                : Complaint.Status.RESOLVED;
        complaint.setStatus(newStatus);
        complaint.setHandlerId(adminUserId);
        complaint.setHandledAt(LocalDateTime.now());
        Complaint saved = complaintRepository.save(complaint);

        operationLogService.record(adminUserId, "COMPLAINT_" + newStatus.name(), complaintId,
                "playerId=" + complaint.getPlayerId());

        return saved;
    }
}
