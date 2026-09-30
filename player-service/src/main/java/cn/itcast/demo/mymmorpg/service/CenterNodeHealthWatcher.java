package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.rpc.CenterSceneRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Center 节点健康：心跳超时标记 DOWN，并触发该节点在线玩家强制迁移标记。
 */
@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
@EnableConfigurationProperties(SceneRuntimeProperties.class)
public class CenterNodeHealthWatcher {

    private static final Logger log = LoggerFactory.getLogger(CenterNodeHealthWatcher.class);
    private static final String DOWN_SET = "center:node:down";
    private static final String MIGRATE_PREFIX = "center:migrate:pending:";

    private final CenterSceneRegistry centerSceneRegistry;
    private final SceneRuntimeProperties sceneRuntimeProperties;
    private final PlayerSessionService playerSessionService;
    private final StringRedisTemplate redis;

    public CenterNodeHealthWatcher(CenterSceneRegistry centerSceneRegistry,
                                   SceneRuntimeProperties sceneRuntimeProperties,
                                   PlayerSessionService playerSessionService,
                                   StringRedisTemplate redis) {
        this.centerSceneRegistry = centerSceneRegistry;
        this.sceneRuntimeProperties = sceneRuntimeProperties;
        this.playerSessionService = playerSessionService;
        this.redis = redis;
    }

    @Scheduled(fixedDelayString = "${game.scene.node-health-check-ms:5000}")
    public void check() {
        long staleMs = Math.max(5_000L, sceneRuntimeProperties.getNodeStaleMs());
        // 先收集即将剔除的节点
        Set<String> staleNodes = new HashSet<>();
        long cutoff = System.currentTimeMillis() - staleMs;
        for (CenterSceneRegistry.SceneServerNode n : centerSceneRegistry.listAll()) {
            if (n.getLastHeartbeatMillis() > 0 && n.getLastHeartbeatMillis() < cutoff) {
                staleNodes.add(n.getNodeId());
            }
        }
        int purged = centerSceneRegistry.purgeStale(staleMs);
        for (String nodeId : staleNodes) {
            markDownAndScheduleMigration(nodeId);
        }
        if (purged > 0) {
            log.warn("center purged {} stale scene bindings, downNodes={}", purged, staleNodes);
        }
    }

    private void markDownAndScheduleMigration(String nodeId) {
        try {
            redis.opsForSet().add(DOWN_SET, nodeId);
            Set<String> players = playerSessionService.listOnlineOnNode(nodeId);
            for (String pid : players) {
                redis.opsForSet().add(MIGRATE_PREFIX + nodeId, pid);
                // 标记需重新分配：客户端下次心跳/操作可感知并通过 session_ticket 重定向
                redis.opsForValue().set("center:force_migrate:" + pid, nodeId);
            }
            log.warn("node {} DOWN, pending migrate players={}", nodeId, players.size());
        } catch (Exception e) {
            log.warn("mark node down failed {}: {}", nodeId, e.getMessage());
        }
    }
}
