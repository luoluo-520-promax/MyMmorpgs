package cn.itcast.demo.mymmorpg.rpc;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Center 节点注册 / 心跳超时 purge。
 */
public class CenterSceneRegistryHealthTest {

    @Test
    public void purgeStale_removesTimedOutNodes() throws Exception {
        CenterSceneRegistry registry = new CenterSceneRegistry();
        registry.registerNode("n1", "127.0.0.1", 9001, List.of(10, 11));
        assertThat(registry.find(10)).isNotNull();
        assertThat(registry.isSceneReachable(10)).isTrue();

        // 伪造过期心跳：重新放入旧心跳节点
        CenterSceneRegistry.SceneServerNode stale = new CenterSceneRegistry.SceneServerNode(
                10, 1, "n1", "127.0.0.1", 9001, 10, System.currentTimeMillis() - 60_000L);
        registry.register(stale);
        registry.register(new CenterSceneRegistry.SceneServerNode(
                11, 1, "n1", "127.0.0.1", 9001, 11, System.currentTimeMillis() - 60_000L));

        int removed = registry.purgeStale(5_000L);
        assertThat(removed).isGreaterThanOrEqualTo(2);
        assertThat(registry.find(10)).isNull();
        assertThat(registry.listAll()).isEmpty();
    }

    @Test
    public void heartbeat_refreshesAndKeepsReachable() {
        CenterSceneRegistry registry = new CenterSceneRegistry();
        registry.registerNode("n2", "10.0.0.2", 8080, List.of(3));
        registry.heartbeat("n2", List.of(3));
        assertThat(registry.purgeStale(30_000L)).isEqualTo(0);
        assertThat(registry.find(3).getNodeId()).isEqualTo("n2");
    }

    @Test
    public void unregisterNode_clearsBindings() {
        CenterSceneRegistry registry = new CenterSceneRegistry();
        registry.registerNode("down", "1.1.1.1", 1, List.of(5, 6));
        registry.unregisterNode("down");
        assertThat(registry.listAll()).isEmpty();
    }
}
