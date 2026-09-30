package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ActivityImportClient;
import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.model.admin.ActivityImportDryRunResult;
import cn.itcast.demo.mymmorpg.model.admin.ImportResultItem;
import cn.itcast.demo.mymmorpg.service.ai.AiActivityDraftStore;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAiActivityServiceTest {

    private AdminPermissionService permissionService;
    private AdminOperationLogService operationLogService;
    private ActivityImportClient activityImportClient;
    private AiModelClient aiModelClient;
    private AdminAiActivityService service;

    @BeforeMethod
    public void setUp() {
        permissionService = mock(AdminPermissionService.class);
        operationLogService = mock(AdminOperationLogService.class);
        activityImportClient = mock(ActivityImportClient.class);
        aiModelClient = mock(AiModelClient.class);
        AdminAiProperties properties = new AdminAiProperties();
        properties.setEnabled(false);
        properties.setPromptVersion("activity-copilot-v1");
        when(aiModelClient.generateActivityJson(anyString(), anyString())).thenReturn(Optional.empty());
        service = new AdminAiActivityService(
                permissionService,
                operationLogService,
                activityImportClient,
                properties,
                aiModelClient,
                new AiActivityDraftStore(),
                new ObjectMapper(),
                new AdminAiPlatformBridge(false, EmptyAiPlatformClientProvider.INSTANCE));
    }

    @Test
    public void draft_deniedWithoutPermission() {
        when(permissionService.hasPermission(1L, AdminAiActivityService.PERM_AI_ACTIVITY)).thenReturn(false);
        assertThatThrownBy(() -> service.draft(1L, "做个签到活动", "checkin", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void draft_checkinTemplate_returnsConfirmToken() {
        when(permissionService.hasPermission(1L, AdminAiActivityService.PERM_AI_ACTIVITY)).thenReturn(true);
        when(activityImportClient.dryRunJson(anyString()))
                .thenReturn(ActivityImportDryRunResult.ok(1, List.of(), List.of()));

        Map<String, Object> result = service.draft(1L, "做一周限时登录签到活动", "checkin", true);

        assertThat(result.get("status")).isEqualTo("OK");
        assertThat(result.get("template")).isEqualTo("checkin");
        assertThat(result.get("model")).isEqualTo("template");
        assertThat(result.get("confirmToken")).isNotNull();
        assertThat(result.get("document")).isNotNull();
        verify(operationLogService).record(eq(1L), eq("AI_ACTIVITY_DRAFT"), eq(null), anyString());
    }

    @Test
    public void apply_importsAfterConfirm() {
        when(permissionService.hasPermission(1L, AdminAiActivityService.PERM_AI_ACTIVITY)).thenReturn(true);
        when(activityImportClient.dryRunJson(anyString()))
                .thenReturn(ActivityImportDryRunResult.ok(1, List.of(), List.of()));
        when(activityImportClient.importJson(anyString()))
                .thenReturn(List.of(ImportResultItem.activity(99L, 2)));

        Map<String, Object> draft = service.draft(1L, "签到活动", "checkin", true);
        String token = (String) draft.get("confirmToken");

        Map<String, Object> applied = service.apply(1L, token);
        assertThat(applied.get("status")).isEqualTo("OK");
        assertThat(applied.get("count")).isEqualTo(1);
        verify(operationLogService).record(eq(1L), eq("AI_ACTIVITY_APPLY"), eq(null), anyString());
    }

    @Test
    public void apply_rejectsMissingToken() {
        when(permissionService.hasPermission(1L, AdminAiActivityService.PERM_AI_ACTIVITY)).thenReturn(true);
        assertThatThrownBy(() -> service.apply(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirmToken");
    }
}
