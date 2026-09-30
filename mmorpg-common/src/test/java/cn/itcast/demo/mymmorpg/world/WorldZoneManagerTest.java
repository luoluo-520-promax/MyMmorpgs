package cn.itcast.demo.mymmorpg.world;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WorldZoneManagerTest {

    @Test
    public void splitOnHighDensityAndDetectHandoff() {
        WorldZoneManager mgr = new WorldZoneManager();
        mgr.configure(10, 2, 8);
        WorldZoneManager.ZoneShard root = mgr.ensureWorld(1, "node-a");
        assertThat(root.zoneId()).isPositive();

        mgr.reportPlayerCount(1, root.zoneId(), 12);
        assertThat(mgr.listZones(1).size()).isGreaterThanOrEqualTo(2);

        WorldZoneManager.ZoneShard a = mgr.listZones(1).get(0);
        WorldZoneManager.ZoneShard b = mgr.listZones(1).stream()
                .filter(z -> z.zoneId() != a.zoneId())
                .findFirst()
                .orElseThrow();

        float fromX = a.cellMinX() * 100f + 10f;
        float fromZ = a.cellMinZ() * 100f + 10f;
        float toX = b.cellMinX() * 100f + 10f;
        float toZ = b.cellMinZ() * 100f + 10f;
        WorldZoneManager.BorderHandoff handoff = mgr.detectHandoff(1, fromX, fromZ, toX, toZ, 100);
        assertThat(handoff.required()).isTrue();
        assertThat(handoff.fromZoneId()).isNotEqualTo(handoff.toZoneId());
    }
}
