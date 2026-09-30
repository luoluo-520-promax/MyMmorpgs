package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.center.HttpCenterRoutingClient;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import cn.itcast.demo.mymmorpg.config.CenterRoutingProperties;
import cn.itcast.demo.mymmorpg.rpc.CenterSceneRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 场景跨服路由门面：local 用进程内目录；remote 调远端 Center HTTP（对齐 MyLunarCore）。
 */
@Component
@EnableConfigurationProperties(CenterRoutingProperties.class)
public class CenterSceneRouter {

    private static final Logger log = LoggerFactory.getLogger(CenterSceneRouter.class);

    private final CenterSceneRegistry centerSceneRegistry;
    private final CenterRoutingProperties properties;
    private final HttpCenterRoutingClient httpCenterRoutingClient;

    public CenterSceneRouter(CenterSceneRegistry centerSceneRegistry,
                             CenterRoutingProperties properties,
                             HttpCenterRoutingClient httpCenterRoutingClient) {
        this.centerSceneRegistry = centerSceneRegistry;
        this.properties = properties;
        this.httpCenterRoutingClient = httpCenterRoutingClient;
    }

    public boolean isSceneReachable(int sceneId) {
        try {
            planMigration(sceneId);
            return true;
        } catch (Exception e) {
            log.debug("scene not reachable sceneId={}: {}", sceneId, e.getMessage());
            return false;
        }
    }

    /**
     * 解析目标场景迁移计划；remote 失败且无远端 URL 时回退本地目录。
     */
    public SceneMigrationPlan planMigration(int sceneId) {
        if (properties.isRemoteMode()) {
            String base = properties.getRemoteBaseUrl();
            if (base != null && !base.isBlank()) {
                return httpCenterRoutingClient.planMigration(sceneId);
            }
            log.debug("game.center.mode=remote but remoteBaseUrl empty; fallback local for sceneId={}", sceneId);
        }
        return planLocal(sceneId);
    }

    private SceneMigrationPlan planLocal(int sceneId) {
        if (!centerSceneRegistry.isSceneReachable(sceneId)) {
            throw new IllegalStateException("scene not in center registry: " + sceneId);
        }
        CenterSceneRegistry.SceneServerNode node = centerSceneRegistry.find(sceneId);
        String nodeId = properties.getLocalNodeId() == null ? "local" : properties.getLocalNodeId();
        String host = properties.getAdvertiseHost();
        int port = properties.getAdvertisePort();
        if (node != null) {
            nodeId = node.getNodeId() != null ? node.getNodeId() : String.valueOf(node.getServerId());
            host = node.getIp();
            port = node.getPort();
            int zoneId = node.getZoneId() > 0 ? node.getZoneId() : sceneId;
            boolean local = properties.getLocalNodeId() != null
                    && (properties.getLocalNodeId().equalsIgnoreCase(nodeId)
                    || ("local".equalsIgnoreCase(properties.getLocalNodeId()) && node.getServerId() == 0));
            if (!local && host != null && !host.isBlank() && port > 0) {
                return SceneMigrationPlan.remotePlan(sceneId, zoneId, nodeId, host, port);
            }
        }
        return SceneMigrationPlan.localPlan(sceneId, properties.getLocalNodeId(), host, port);
    }

    public String mode() {
        return properties.getMode();
    }

    public String localNodeId() {
        return properties.getLocalNodeId();
    }

    public CenterRoutingProperties properties() {
        return properties;
    }
}
