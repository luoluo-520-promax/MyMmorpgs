package cn.itcast.demo.mymmorpg.gateway;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayZoneRouteTableTest {

    @Test
    public void lookupCrossZone() {
        GatewayZoneRouteTable table = new GatewayZoneRouteTable();
        var local = table.lookup(1, 100f, 100f, 100, "scene-local");
        assertThat(local.found()).isTrue();
        assertThat(local.sameNode()).isTrue();
        var remote = table.lookup(1, 900f, 100f, 100, "scene-local");
        assertThat(remote.found()).isTrue();
        assertThat(remote.sameNode()).isFalse();
        assertThat(remote.nodeId()).isEqualTo("scene-liyue");
    }
}
