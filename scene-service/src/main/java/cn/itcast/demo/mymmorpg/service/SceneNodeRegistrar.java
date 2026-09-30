package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.center.HttpCenterRoutingClient;
import cn.itcast.demo.mymmorpg.config.CenterRoutingProperties;
import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.entity.MapConfig;
import cn.itcast.demo.mymmorpg.rpc.CenterSceneRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景节点向 Center 注册 / 心跳 / 注销。
 * 默认关闭；设置 {@code game.scene.register-to-center=true} 后启用。
 */
@Component
@ConditionalOnProperty(name = "game.scene.register-to-center", havingValue = "true")
@EnableConfigurationProperties({SceneRuntimeProperties.class, CenterRoutingProperties.class})
public class SceneNodeRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SceneNodeRegistrar.class);

    private final SceneRuntimeProperties sceneRuntimeProperties;
    private final CenterRoutingProperties centerRoutingProperties;
    private final CenterSceneRegistry centerSceneRegistry;
    private final ConfigQueryService configQueryService;
    private final HttpCenterRoutingClient httpCenterRoutingClient;

    public SceneNodeRegistrar(SceneRuntimeProperties sceneRuntimeProperties,
                              CenterRoutingProperties centerRoutingProperties,
                              CenterSceneRegistry centerSceneRegistry,
                              ConfigQueryService configQueryService,
                              HttpCenterRoutingClient httpCenterRoutingClient) {
        this.sceneRuntimeProperties = sceneRuntimeProperties;
        this.centerRoutingProperties = centerRoutingProperties;
        this.centerSceneRegistry = centerSceneRegistry;
        this.configQueryService = configQueryService;
        this.httpCenterRoutingClient = httpCenterRoutingClient;
        registerOnce();
    }

    @Scheduled(fixedDelayString = "${game.scene.node-heartbeat-ms:15000}")
    public void heartbeat() {
        List<Integer> owned = resolveOwnedSceneIds();
        if (owned.isEmpty()) {
            return;
        }
        String nodeId = centerRoutingProperties.getLocalNodeId();
        String host = centerRoutingProperties.getAdvertiseHost();
        int port = centerRoutingProperties.getAdvertisePort();
        if (centerRoutingProperties.isRemoteMode()
                && centerRoutingProperties.getRemoteBaseUrl() != null
                && !centerRoutingProperties.getRemoteBaseUrl().isBlank()) {
            try {
                httpCenterRoutingClient.registerNode(nodeId, host, port, owned);
                httpCenterRoutingClient.heartbeatNode(nodeId, owned);
            } catch (Exception e) {
                log.warn("remote center heartbeat failed: {}", e.getMessage());
            }
            return;
        }
        centerSceneRegistry.registerNode(nodeId, host, port, owned);
        centerSceneRegistry.heartbeat(nodeId, owned);
        centerSceneRegistry.purgeStale(sceneRuntimeProperties.getNodeStaleMs());
    }

    @PreDestroy
    public void unregister() {
        List<Integer> owned = resolveOwnedSceneIds();
        String nodeId = centerRoutingProperties.getLocalNodeId();
        if (centerRoutingProperties.isRemoteMode()
                && centerRoutingProperties.getRemoteBaseUrl() != null
                && !centerRoutingProperties.getRemoteBaseUrl().isBlank()) {
            try {
                httpCenterRoutingClient.unregisterNode(nodeId);
            } catch (Exception e) {
                log.debug("remote unregister failed: {}", e.getMessage());
            }
            return;
        }
        centerSceneRegistry.unregisterNode(nodeId);
        for (Integer sid : owned) {
            centerSceneRegistry.unregister(sid);
        }
    }

    private void registerOnce() {
        List<Integer> owned = resolveOwnedSceneIds();
        if (owned.isEmpty()) {
            log.warn("scene register-to-center enabled but owned scene list empty");
            return;
        }
        String nodeId = centerRoutingProperties.getLocalNodeId();
        String host = centerRoutingProperties.getAdvertiseHost();
        int port = centerRoutingProperties.getAdvertisePort();
        if (centerRoutingProperties.isRemoteMode()
                && centerRoutingProperties.getRemoteBaseUrl() != null
                && !centerRoutingProperties.getRemoteBaseUrl().isBlank()) {
            try {
                httpCenterRoutingClient.registerNode(nodeId, host, port, owned);
                log.info("registered {} scenes to remote center as node={}", owned.size(), nodeId);
            } catch (Exception e) {
                log.warn("remote center register failed: {}", e.getMessage());
            }
            return;
        }
        centerSceneRegistry.registerNode(nodeId, host, port, owned);
        log.info("registered {} scenes to local center as node={}", owned.size(), nodeId);
    }

    private List<Integer> resolveOwnedSceneIds() {
        List<Integer> configured = sceneRuntimeProperties.getOwnedSceneIds();
        if (configured != null && !configured.isEmpty()) {
            return new ArrayList<>(configured);
        }
        List<Integer> all = new ArrayList<>();
        for (MapConfig map : configQueryService.listAllMaps()) {
            if (map != null && map.getId() != null) {
                all.add(map.getId());
            }
        }
        return all;
    }
}
