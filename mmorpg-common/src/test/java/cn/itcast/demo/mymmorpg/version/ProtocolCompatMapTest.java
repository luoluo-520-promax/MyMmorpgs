package cn.itcast.demo.mymmorpg.version;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocolCompatMapTest {

    private ProtocolCompatMap map;

    @BeforeMethod
    public void setUp() {
        map = new ProtocolCompatMap();
        map.setMinSupportedVersion(10000L);
        map.setCurrentProtocolHash("proto-v2-current");
        map.registerRule("proto-v1", 10000L, ProtocolCompatMap.Policy.SOFT,
                java.util.Set.of(2600, 2601, 2602, 2603, 2604, 2605));
        map.registerRule("proto-combat-v2", 15000L, ProtocolCompatMap.Policy.HARD, java.util.Set.of());
    }

    @Test
    public void hardRejectWhenBelowMinVersion() {
        ProtocolCompatMap.CompatResult r = map.evaluate(9000, "proto-v1");
        assertThat(r.hardReject()).isTrue();
        assertThat(r.retCode()).isEqualTo(RetCode.CLIENT_TOO_OLD);
    }

    @Test
    public void softBlockKnownOldProtocol() {
        ProtocolCompatMap.CompatResult r = map.evaluate(15000, "proto-v1");
        assertThat(r.hardReject()).isFalse();
        assertThat(r.blockedMsgIds()).contains(2600);
    }

    @Test
    public void hardRejectUnknownProtocol() {
        ProtocolCompatMap.CompatResult r = map.evaluate(15000, "proto-unknown");
        assertThat(r.hardReject()).isTrue();
    }
}
