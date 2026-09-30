package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.rpc.CenterSceneRegistry;
import cn.itcast.demo.mymmorpg.service.PlayerSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 运营在线统计：总在线、节点分布、场景人数。
 */
@RestController
@RequestMapping("/internal/online")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
public class InternalOnlineStatsController {

    private final PlayerSessionService playerSessionService;
    private final CenterSceneRegistry centerSceneRegistry;

    public InternalOnlineStatsController(PlayerSessionService playerSessionService,
                                         CenterSceneRegistry centerSceneRegistry) {
        this.playerSessionService = playerSessionService;
        this.centerSceneRegistry = centerSceneRegistry;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalOnline", playerSessionService.countOnline());

        Set<String> nodeIds = new HashSet<>();
        for (CenterSceneRegistry.SceneServerNode n : centerSceneRegistry.listAll()) {
            nodeIds.add(n.getNodeId());
        }

        Map<String, Long> byScene = new HashMap<>();
        for (String id : playerSessionService.listOnlinePlayerIds()) {
            long playerId;
            try {
                playerId = Long.parseLong(id);
            } catch (NumberFormatException e) {
                continue;
            }
            Map<String, String> f = playerSessionService.getOnlineFields(playerId);
            nodeIds.add(f.getOrDefault("node_id", "local"));
            byScene.merge(f.getOrDefault("scene_id", "0"), 1L, Long::sum);
        }

        Map<String, Long> byNode = new LinkedHashMap<>();
        for (String nodeId : nodeIds) {
            byNode.put(nodeId, playerSessionService.countOnlineOnNode(nodeId));
        }
        body.put("byNode", byNode);
        body.put("byScene", byScene);
        body.put("nodesRegistered", centerSceneRegistry.listAll().size());
        return body;
    }
}
