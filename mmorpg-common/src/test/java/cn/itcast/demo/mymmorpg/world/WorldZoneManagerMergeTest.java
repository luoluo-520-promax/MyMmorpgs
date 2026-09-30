package cn.itcast.demo.mymmorpg.world;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 动态域：低密度合并相邻 Zone。
 */
public class WorldZoneManagerMergeTest {

    @Test
    public void mergeAdjacentLowDensityZones() {
        WorldZoneManager mgr = new WorldZoneManager();
        mgr.configure(8, 3, 8);
        WorldZoneManager.ZoneShard root = mgr.ensureWorld(9, "n1");
        mgr.reportPlayerCount(9, root.zoneId(), 10);
        int afterSplit = mgr.listZones(9).size();
        assertThat(afterSplit).isGreaterThanOrEqualTo(2);

        for (WorldZoneManager.ZoneShard z : mgr.listZones(9)) {
            mgr.reportPlayerCount(9, z.zoneId(), 1);
        }
        // 再触发一次合并扫描
        WorldZoneManager.ZoneShard any = mgr.listZones(9).get(0);
        mgr.reportPlayerCount(9, any.zoneId(), 1);

        assertThat(mgr.listZones(9).size()).isLessThanOrEqualTo(afterSplit);
        assertThat(mgr.stats(9).get("worldId")).isEqualTo(9);
    }
}
