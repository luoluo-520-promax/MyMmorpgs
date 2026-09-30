package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.center.HttpCenterRoutingClient;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import cn.itcast.demo.mymmorpg.config.CenterRoutingProperties;
import cn.itcast.demo.mymmorpg.rpc.CenterSceneRegistry;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CenterSceneRouterTest {

    @Test
    public void planMigration_local_usesRegistryAdvertise() {
        CenterSceneRegistry registry = new CenterSceneRegistry();
        CenterRoutingProperties props = new CenterRoutingProperties();
        props.setMode("local");
        props.setLocalNodeId("local");
        props.setAdvertiseHost("127.0.0.1");
        props.setAdvertisePort(8089);
        HttpCenterRoutingClient http = mock(HttpCenterRoutingClient.class);
        CenterSceneRouter router = new CenterSceneRouter(registry, props, http);

        SceneMigrationPlan plan = router.planMigration(1001);
        assertThat(plan.local()).isTrue();
        assertThat(plan.sceneId()).isEqualTo(1001);
        assertThat(plan.nodeHost()).isEqualTo("127.0.0.1");
        verify(http, never()).planMigration(1001);
    }

    @Test
    public void planMigration_remote_delegatesToHttp() {
        CenterSceneRegistry registry = new CenterSceneRegistry();
        CenterRoutingProperties props = new CenterRoutingProperties();
        props.setMode("remote");
        props.setRemoteBaseUrl("http://center:8989");
        props.setLocalNodeId("node-a");
        HttpCenterRoutingClient http = mock(HttpCenterRoutingClient.class);
        when(http.planMigration(7)).thenReturn(
                SceneMigrationPlan.remotePlan(7, 7, "node-b", "10.0.0.2", 9000));
        CenterSceneRouter router = new CenterSceneRouter(registry, props, http);

        SceneMigrationPlan plan = router.planMigration(7);
        assertThat(plan.local()).isFalse();
        assertThat(plan.nodeHost()).isEqualTo("10.0.0.2");
        assertThat(plan.nodePort()).isEqualTo(9000);
    }
}
