package cn.itcast.demo.mymmorpg.world;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ZoneLoadBalancerTest {

    @Test
    public void rebalanceSplitsOnHighDensityAndTracksMetrics() {
        WorldZoneManager mgr = new WorldZoneManager();
        mgr.configure(10, 2, 8);
        WorldZoneManager.ZoneShard root = mgr.ensureWorld(42, "node-a");
        ZoneLoadBalancer balancer = new ZoneLoadBalancer(mgr);

        Map<String, Object> result = balancer.rebalance(42, Map.of(root.zoneId(), 12));
        assertThat(result.get("ok")).isEqualTo(true);
        assertThat((Integer) result.get("zoneCountAfter")).isGreaterThanOrEqualTo(2);
        assertThat(balancer.metrics().get("rebalanceRuns")).isEqualTo(1L);
        assertThat((Long) balancer.metrics().get("splits")).isPositive();

        Map<Integer, String> assignment = balancer.suggestNodeAssignment(42);
        assertThat(assignment).isNotEmpty();
        assertThat(assignment.values()).allSatisfy(n -> assertThat(n).isNotBlank());
    }

    @Test
    public void suggestNodeAssignmentBalancesLoad() {
        WorldZoneManager mgr = new WorldZoneManager();
        mgr.configure(100, 1, 8);
        WorldZoneManager.ZoneShard root = mgr.ensureWorld(7, "node-a");
        // 人为制造高密度拆分出 node-a-b
        mgr.reportPlayerCount(7, root.zoneId(), 120);
        ZoneLoadBalancer balancer = new ZoneLoadBalancer(mgr);
        Map<Integer, String> assignment = balancer.suggestNodeAssignment(7);
        assertThat(assignment.size()).isEqualTo(mgr.listZones(7).size());
        assertThat(assignment.values()).allMatch(n -> n.equals("node-a") || n.startsWith("node-a"));
    }
}
