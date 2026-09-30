package cn.itcast.demo.mymmorpg.gm;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GmCommandDispatcherTest {

    @Test
    public void permissionAndDispatch() {
        GmCommandDispatcher d = new GmCommandDispatcher();
        d.register(new GmCommandDispatcher.GmCommand(
                "teleport", GmCommandDispatcher.Level.MODERATOR, "tp",
                args -> Map.of("ok", true, "x", args.get("x"))));
        assertThat(d.dispatch("teleport", GmCommandDispatcher.Level.OBSERVER, Map.of("x", 1))
                .get("error")).isEqualTo("permission_denied");
        assertThat(d.dispatch("teleport", GmCommandDispatcher.Level.ADMIN, Map.of("x", 3))
                .get("ok")).isEqualTo(true);
        assertThat(d.catalog().get("commands")).isNotNull();
    }
}
