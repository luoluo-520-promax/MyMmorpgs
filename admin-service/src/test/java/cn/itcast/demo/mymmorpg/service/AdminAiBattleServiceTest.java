package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.BattleStatsClient;
import cn.itcast.demo.mymmorpg.config.AdminAiProperties;
import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.service.ai.AiModelClient;
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

public class AdminAiBattleServiceTest {

    private AdminPermissionService permissionService;
    private AdminOperationLogService operationLogService;
    private BattleStatsClient battleStatsClient;
    private AiModelClient aiModelClient;
    private AdminAiBattleService service;

    @BeforeMethod
    public void setUp() {
        permissionService = mock(AdminPermissionService.class);
        operationLogService = mock(AdminOperationLogService.class);
        battleStatsClient = mock(BattleStatsClient.class);
        aiModelClient = mock(AiModelClient.class);
        AdminAiProperties properties = new AdminAiProperties();
        properties.setEnabled(false);
        when(aiModelClient.generateBattleReportSummary(anyString())).thenReturn(Optional.empty());
        service = new AdminAiBattleService(
                permissionService, operationLogService, battleStatsClient, aiModelClient, properties);
    }

    @Test
    public void weeklyReport_buildsMarkdownFromSnapshot() {
        when(permissionService.hasPermission(1L, AdminAiBattleService.PERM_BATTLE_ANALYZE)).thenReturn(true);
        BattleStatsSnapshot snap = new BattleStatsSnapshot();
        snap.setEndedTotal(100);
        snap.setWinCount(80);
        snap.setLoseCount(15);
        snap.setDrawCount(5);
        snap.setWinRate(0.8);
        snap.setAvgDurationSec(45.5);
        snap.setIssuedBattleIdMax(10100);
        when(battleStatsClient.snapshot()).thenReturn(snap);

        Map<String, Object> result = service.weeklyReport(1L, "战士胜率偏高");

        assertThat(result.get("status")).isEqualTo("OK");
        assertThat(result.get("markdown")).asString().contains("胜率");
        assertThat(result.get("hypotheses")).asList().isNotEmpty();
        assertThat(result.get("simulationCommands")).asList().isNotEmpty();
        verify(operationLogService).record(eq(1L), eq("AI_BATTLE_WEEKLY_REPORT"), eq(null), anyString());
    }

    @Test
    public void weeklyReport_deniedWithoutPermission() {
        when(permissionService.hasPermission(1L, AdminAiBattleService.PERM_BATTLE_ANALYZE)).thenReturn(false);
        assertThatThrownBy(() -> service.weeklyReport(1L, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
