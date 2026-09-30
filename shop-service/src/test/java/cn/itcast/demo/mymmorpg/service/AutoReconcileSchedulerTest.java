package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AutoReconcileSchedulerTest {

    @Test
    public void mismatchCount_sumsDiffBuckets() {
        Map<String, Object> result = Map.of(
                "paidStuckCount", 2,
                "missingChannelOrderIds", List.of("a"),
                "diffChannelOnly", List.of("c1", "c2"),
                "diffLocalOnly", List.of("l1"),
                "diffPaidStuckVsChannelSuccess", List.of("p1", "p2", "p3"));
        assertThat(AutoReconcileScheduler.mismatchCount(result)).isEqualTo(2 + 1 + 2 + 1 + 3);
    }
}
