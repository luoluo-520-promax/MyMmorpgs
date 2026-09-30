package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenWorldRuntimeServiceTest {

    @Test
    public void statusAndMixedLoadAndGm() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        assertThat(runtime.status().get("ok")).isEqualTo(true);
        assertThat(runtime.status()).containsKey("performance");
        assertThat(runtime.worldState().snapshot(1, 0L).get("ok")).isEqualTo(true);
        assertThat(runtime.worldLevel().toView(1, 0L).get("worldLevel")).isEqualTo(3);
        assertThat(runtime.mixedLoadBenchmark(50, 20, 20, 100).get("ok")).isEqualTo(true);
        assertThat(runtime.gm().catalog().get("commands")).isNotNull();
        assertThat(runtime.gm().dispatch("teleport",
                cn.itcast.demo.mymmorpg.gm.GmCommandDispatcher.Level.ADMIN,
                java.util.Map.of("playerId", 1, "x", 10)).get("ok")).isEqualTo(true);
        var prep = runtime.handshake().prepare(
                7L, "scene-local",
                runtime.routingTable().lookup(1, 900f, 50f, 100, "scene-local"),
                1, 1, 900, 0, 50, 0, 0, 0);
        assertThat(prep.get("ok")).isEqualTo(true);
    }

    @Test
    public void gameplayFacadeSeededAndReachableFromStatus() {
        OpenWorldRuntimeService runtime = new OpenWorldRuntimeService();
        assertThat(runtime.gameplay()).isNotNull();
        assertThat(runtime.status().get("gameplay")).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> gp =
                (java.util.Map<String, Object>) runtime.status().get("gameplay");
        assertThat(gp.get("ok")).isEqualTo(true);
        assertThat((Integer) gp.get("explorationPoints")).isGreaterThanOrEqualTo(3);
        assertThat((Integer) gp.get("landmarks")).isGreaterThanOrEqualTo(1);
        assertThat(runtime.gameplay().discoverExploration(
                99L, "vista-sky-ruin", 200f, 40f, 120f, System.currentTimeMillis())
                .get("ok")).isEqualTo(true);
    }
}
