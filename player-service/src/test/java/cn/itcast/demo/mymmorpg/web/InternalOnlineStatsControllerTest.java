package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.rpc.CenterSceneRegistry;
import cn.itcast.demo.mymmorpg.service.PlayerSessionService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * /internal/online/stats 运营统计接口。
 */
public class InternalOnlineStatsControllerTest {

    private PlayerSessionService playerSessionService;
    private CenterSceneRegistry centerSceneRegistry;
    private InternalOnlineStatsController controller;

    @BeforeMethod
    public void setUp() {
        playerSessionService = mock(PlayerSessionService.class);
        centerSceneRegistry = new CenterSceneRegistry();
        controller = new InternalOnlineStatsController(playerSessionService, centerSceneRegistry);
    }

    @Test
    public void stats_aggregatesTotalNodeAndScene() {
        centerSceneRegistry.registerNode("node-a", "127.0.0.1", 8089, List.of(1, 2));
        when(playerSessionService.countOnline()).thenReturn(2L);
        when(playerSessionService.listOnlinePlayerIds()).thenReturn(Set.of("11", "12"));
        when(playerSessionService.getOnlineFields(11L)).thenReturn(Map.of("node_id", "node-a", "scene_id", "1"));
        when(playerSessionService.getOnlineFields(12L)).thenReturn(Map.of("node_id", "node-b", "scene_id", "2"));
        when(playerSessionService.countOnlineOnNode("node-a")).thenReturn(1L);
        when(playerSessionService.countOnlineOnNode("node-b")).thenReturn(1L);

        Map<String, Object> body = controller.stats();

        assertThat(body.get("totalOnline")).isEqualTo(2L);
        assertThat(body.get("nodesRegistered")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        Map<String, Long> byNode = (Map<String, Long>) body.get("byNode");
        assertThat(byNode).containsEntry("node-a", 1L).containsEntry("node-b", 1L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byScene = (Map<String, Long>) body.get("byScene");
        assertThat(byScene).containsEntry("1", 1L).containsEntry("2", 1L);
    }
}
