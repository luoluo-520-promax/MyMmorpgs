package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Complaint;
import cn.itcast.demo.mymmorpg.repository.ComplaintRepository;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AdminComplaintServiceTest {

    private AdminPermissionService permissionService;
    private ComplaintRepository complaintRepository;
    private AdminOperationLogService operationLogService;
    private AdminComplaintService service;

    @BeforeMethod
    public void setUp() {
        permissionService = mock(AdminPermissionService.class);
        complaintRepository = mock(ComplaintRepository.class);
        operationLogService = mock(AdminOperationLogService.class);
        service = new AdminComplaintService(permissionService, complaintRepository, operationLogService);
    }

    @Test
    public void handleComplaint_deniedWithoutPermission() {
        when(permissionService.hasPermission(1L, AdminComplaintService.PERM_COMPLAINT_HANDLE)).thenReturn(false);
        assertThatThrownBy(() -> service.handleComplaint(1L, 99L, "RESOLVE"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void handleComplaint_resolvesPending() {
        Complaint complaint = new Complaint();
        complaint.setId(10L);
        complaint.setPlayerId(5L);
        complaint.setStatus(Complaint.Status.PENDING);
        when(permissionService.hasPermission(1L, AdminComplaintService.PERM_COMPLAINT_HANDLE)).thenReturn(true);
        when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Complaint result = service.handleComplaint(1L, 10L, "RESOLVE");

        assertThat(result.getStatus()).isEqualTo(Complaint.Status.RESOLVED);
        assertThat(result.getHandlerId()).isEqualTo(1L);
        verify(operationLogService).record(eq(1L), eq("COMPLAINT_RESOLVED"), eq(10L), anyString());
    }
}
