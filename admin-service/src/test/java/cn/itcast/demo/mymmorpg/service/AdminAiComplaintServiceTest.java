package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.entity.Complaint;
import cn.itcast.demo.mymmorpg.repository.ComplaintRepository;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAiComplaintServiceTest {

    private AdminPermissionService permissionService;
    private AdminOperationLogService operationLogService;
    private ComplaintRepository complaintRepository;
    private AiModelClient aiModelClient;
    private AdminAiComplaintService service;

    @BeforeMethod
    public void setUp() {
        permissionService = mock(AdminPermissionService.class);
        operationLogService = mock(AdminOperationLogService.class);
        complaintRepository = mock(ComplaintRepository.class);
        aiModelClient = mock(AiModelClient.class);
        AdminAiProperties properties = new AdminAiProperties();
        properties.setEnabled(false);
        when(aiModelClient.generateComplaintAdvice(anyString())).thenReturn(Optional.empty());
        service = new AdminAiComplaintService(
                permissionService, operationLogService, complaintRepository,
                aiModelClient, properties, new ObjectMapper());
    }

    @Test
    public void suggest_paymentCategory() {
        when(permissionService.hasPermission(1L, AdminComplaintService.PERM_COMPLAINT_HANDLE)).thenReturn(true);

        Map<String, Object> result = service.suggest(1L, null, "充值没到账，订单号 ABC");

        assertThat(result.get("category")).isEqualTo("PAYMENT");
        assertThat(result.get("priority")).isEqualTo("P0");
        assertThat(result.get("suggestedScript")).asString().contains("充值");
        verify(operationLogService).record(eq(1L), eq("AI_COMPLAINT_SUGGEST"), eq(null), anyString());
    }

    @Test
    public void suggest_byComplaintId() {
        Complaint c = new Complaint();
        c.setId(9L);
        c.setPlayerId(42L);
        c.setContent("游戏一直掉线");
        c.setStatus(Complaint.Status.PENDING);
        when(permissionService.hasPermission(1L, AdminComplaintService.PERM_COMPLAINT_HANDLE)).thenReturn(true);
        when(complaintRepository.findById(9L)).thenReturn(Optional.of(c));

        Map<String, Object> result = service.suggest(1L, 9L, null);

        assertThat(result.get("category")).isEqualTo("TECHNICAL");
        assertThat(result.get("playerId")).isEqualTo(42L);
        verify(operationLogService).record(eq(1L), eq("AI_COMPLAINT_SUGGEST"), eq(9L), anyString());
    }

    @Test
    public void suggest_deniedWithoutPermission() {
        when(permissionService.hasPermission(1L, AdminComplaintService.PERM_COMPLAINT_HANDLE)).thenReturn(false);
        assertThatThrownBy(() -> service.suggest(1L, null, "test"))
                .isInstanceOf(IllegalStateException.class);
    }
}
