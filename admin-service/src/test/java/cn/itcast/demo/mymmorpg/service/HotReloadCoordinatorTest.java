package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.ActivityOpsClient;
import cn.itcast.demo.mymmorpg.client.PlayerOpsClient;
import cn.itcast.demo.mymmorpg.client.QuestOpsClient;
import cn.itcast.demo.mymmorpg.client.UpdateOpsClient;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HotReloadCoordinatorTest {

    private ActivityOpsClient activityOpsClient;
    private UpdateOpsClient updateOpsClient;
    private QuestOpsClient questOpsClient;
    private PlayerOpsClient playerOpsClient;
    private ConfigPublishAuditService auditService;
    private HotReloadCoordinator coordinator;

    @BeforeMethod
    public void setUp() {
        activityOpsClient = mock(ActivityOpsClient.class);
        updateOpsClient = mock(UpdateOpsClient.class);
        questOpsClient = mock(QuestOpsClient.class);
        playerOpsClient = mock(PlayerOpsClient.class);
        auditService = new ConfigPublishAuditService();
        coordinator = new HotReloadCoordinator(
                activityOpsClient, updateOpsClient, questOpsClient, playerOpsClient, auditService);
    }

    @Test
    public void reloadAllStaged_success() {
        when(activityOpsClient.reload()).thenReturn(Map.of("ok", true));
        when(updateOpsClient.reload()).thenReturn(Map.of("ok", true));
        when(questOpsClient.reload()).thenReturn(Map.of("ok", true, "count", 3));
        when(playerOpsClient.reload()).thenReturn(Map.of("ok", true, "clearedCaches", java.util.List.of("mapConfigById")));

        HotReloadCoordinator.ReloadResult result = coordinator.reloadAllStaged("ops");

        assertThat(result.success()).isTrue();
        assertThat(result.stages()).contains(
                "prepare:activity", "prepare:update", "prepare:quest", "prepare:configCache",
                "swap:activity", "swap:update", "swap:quest", "swap:configCache");
        assertThat(auditService.lastSuccessful()).isNotNull();
        assertThat(auditService.lastSuccessful().action()).isEqualTo("reloadAllSnapshot");
    }

    @Test
    public void reloadAllStaged_failsWhenActivityDown() {
        when(activityOpsClient.reload()).thenThrow(new RuntimeException("connection refused"));

        HotReloadCoordinator.ReloadResult result = coordinator.reloadAllStaged("ops");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("connection refused");
        assertThat(auditService.lastSuccessful()).isNull();
        assertThat(auditService.recent(5)).hasSize(1);
        assertThat(auditService.recent(5).get(0).success()).isFalse();
    }
}
