package cn.itcast.demo.mymmorpg.world.migrate;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PlayerProxyHandshakeTest {

    @Test
    public void prepareCommitAbort() {
        GatewayRoutingTable table = new GatewayRoutingTable();
        long now = System.currentTimeMillis();
        table.upsert(new GatewayRoutingTable.SceneNodeRoute(
                "a", "127.0.0.1", 8082, 1, 1, 0, 7, 0, 7, now));
        table.upsert(new GatewayRoutingTable.SceneNodeRoute(
                "b", "127.0.0.1", 8083, 1, 2, 8, 15, 0, 7, now));
        PlayerProxyHandshake hs = new PlayerProxyHandshake(new MigrationTicketService());
        var lookup = table.lookup(1, 900f, 100f, 100, "a");
        assertThat(lookup.found()).isTrue();
        assertThat(lookup.sameNode()).isFalse();
        var prep = hs.prepare(42L, "a", lookup, 1, 1, 900, 0, 100, 1, 0, 90);
        assertThat(prep.get("ok")).isEqualTo(true);
        String migrationId = String.valueOf(prep.get("migrationId"));
        assertThat(hs.commit(migrationId).get("phase")).isEqualTo("COMMITTED");
        assertThat(hs.abort(migrationId).get("phase")).isEqualTo("ABORTED");
    }
}
