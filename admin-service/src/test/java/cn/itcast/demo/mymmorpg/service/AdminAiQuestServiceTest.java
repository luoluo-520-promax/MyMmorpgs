package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAiQuestServiceTest {

    private AdminPermissionService permissionService;
    private AdminOperationLogService operationLogService;
    private AiModelClient aiModelClient;
    private AdminAiQuestService service;

    @BeforeMethod
    public void setUp() {
        permissionService = mock(AdminPermissionService.class);
        operationLogService = mock(AdminOperationLogService.class);
        aiModelClient = mock(AiModelClient.class);
        AdminAiProperties properties = new AdminAiProperties();
        properties.setEnabled(false);
        when(aiModelClient.generateQuestDraft(anyString())).thenReturn(Optional.empty());
        service = new AdminAiQuestService(
                permissionService, operationLogService, properties, aiModelClient, new ObjectMapper());
    }

    @Test
    public void draft_deniedWithoutPermission() {
        when(permissionService.hasPermission(1L, AdminAiQuestService.PERM_AI_QUEST)).thenReturn(false);
        when(permissionService.hasPermission(1L, AdminAiActivityService.PERM_AI_ACTIVITY)).thenReturn(false);
        assertThatThrownBy(() -> service.draft(1L, "写一个遗迹调查任务", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void draft_templateFallback_ok() {
        when(permissionService.hasPermission(1L, AdminAiQuestService.PERM_AI_QUEST)).thenReturn(true);
        Map<String, Object> result = service.draft(1L, "调查星轨遗迹的日常任务", true);
        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("dryRun")).isEqualTo(true);
        assertThat(result.get("modelUsed")).isEqualTo("template");
        assertThat(result.get("document")).isNotNull();
        verify(operationLogService).record(eq(1L), eq("AI_QUEST_DRAFT"), anyLong(), anyString());
    }

    @Test
    public void draft_usesLlmWhenAvailable() {
        when(permissionService.hasPermission(1L, AdminAiActivityService.PERM_AI_ACTIVITY)).thenReturn(true);
        AdminAiProperties properties = new AdminAiProperties();
        properties.setEnabled(true);
        properties.setModel("gpt-test");
        when(aiModelClient.generateQuestDraft(anyString())).thenReturn(Optional.of(
                "{\"questId\":80123,\"name\":\"LLM任务\",\"type\":\"MAIN\",\"description\":\"d\","
                        + "\"sceneHint\":\"s\",\"npcHint\":\"n\",\"objectives\":[],\"rewards\":[],\"sceneDetail\":\"x\"}"));
        service = new AdminAiQuestService(
                permissionService, operationLogService, properties, aiModelClient, new ObjectMapper());

        Map<String, Object> result = service.draft(1L, "生成主线任务", true);
        assertThat(result.get("modelUsed")).isEqualTo("gpt-test");
        assertThat(result.get("ok")).isEqualTo(true);
    }

    @Test
    public void draft_blankPromptRejected() {
        when(permissionService.hasPermission(1L, AdminAiQuestService.PERM_AI_QUEST)).thenReturn(true);
        assertThatThrownBy(() -> service.draft(1L, "  ", true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
