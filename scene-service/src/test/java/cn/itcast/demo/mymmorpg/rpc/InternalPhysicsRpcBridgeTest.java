package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P15 RSocket Bridge 单元测试（不启动真实 RSocket 端口）。
 */
public class InternalPhysicsRpcBridgeTest {

    private InternalPhysicsRpcBridge bridge;
    private PhysicsAuthorityService auth;

    @BeforeMethod
    public void setUp() {
        auth = new PhysicsAuthorityService();
        bridge = new InternalPhysicsRpcBridge(auth);
    }

    @Test
    public void handleValidPhysicsRequest() {
        long now = System.currentTimeMillis();
        auth.recordExpected(99L, new PhysicsAuthorityService.ExpectedPhysics(
                2f, 0f, 1f, 1f, 0f, 1f, 0f, now));
        String hash = PhysicsAuthorityService.computeHash(2f, 0f, 1f, 1f, 0f, 1f, 0f);
        String rsp = bridge.handle("99|" + hash + "|2|0|1|1|false");
        assertThat(rsp).contains("ok=true");
    }

    @Test
    public void handleInvalidPayload() {
        String rsp = bridge.handle("not-a-number");
        assertThat(rsp).contains("ok=false");
    }
}
