package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.rpc.CenterSceneRegistry;
import cn.itcast.demo.mymmorpg.service.CenterSceneRouter;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中心服内部 API：供 remote 节点查询迁移计划 / 消费票据 / 节点注册心跳。
 */
@RestController
@RequestMapping("/internal/center")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
@EnableConfigurationProperties(SceneRuntimeProperties.class)
public class InternalCenterController {

    private final CenterSceneRouter centerSceneRouter;
    private final MigrationTicketService migrationTicketService;
    private final CenterSceneRegistry centerSceneRegistry;
    private final SceneRuntimeProperties sceneRuntimeProperties;

    public InternalCenterController(CenterSceneRouter centerSceneRouter,
                                    MigrationTicketService migrationTicketService,
                                    CenterSceneRegistry centerSceneRegistry,
                                    SceneRuntimeProperties sceneRuntimeProperties) {
        this.centerSceneRouter = centerSceneRouter;
        this.migrationTicketService = migrationTicketService;
        this.centerSceneRegistry = centerSceneRegistry;
        this.sceneRuntimeProperties = sceneRuntimeProperties;
    }

    @GetMapping("/plan")
    public Map<String, Object> plan(
            @RequestParam(value = "sceneId", required = false) Integer sceneId,
            @RequestParam(value = "planeId", required = false) Integer planeId,
            @RequestParam(value = "floorId", required = false) Integer floorId) {
        int target = sceneId != null ? sceneId : (planeId != null ? planeId : 0);
        if (target <= 0) {
            throw new IllegalArgumentException("sceneId/planeId is required");
        }
        SceneMigrationPlan plan = centerSceneRouter.planMigration(target);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("zoneId", plan.zoneId());
        body.put("planeId", plan.sceneId());
        body.put("floorId", floorId == null ? 0 : floorId);
        body.put("sceneId", plan.sceneId());
        body.put("nodeId", plan.nodeId());
        body.put("nodeHost", plan.nodeHost() == null ? "" : plan.nodeHost());
        body.put("nodePort", plan.nodePort());
        body.put("local", plan.local());
        return body;
    }

    @PostMapping("/ticket/consume")
    public Map<String, Object> consumeTicket(@RequestBody Map<String, Object> body) {
        String ticket = body == null || body.get("ticket") == null ? null : String.valueOf(body.get("ticket"));
        MigrationTicketService.TicketPayload payload = migrationTicketService.consume(ticket);
        Map<String, Object> resp = new LinkedHashMap<>();
        if (payload == null) {
            resp.put("ok", false);
            resp.put("message", "ticket invalid or expired");
            return resp;
        }
        resp.put("ok", true);
        resp.put("playerId", payload.playerId());
        resp.put("sceneId", payload.sceneId());
        resp.put("lineId", payload.lineId());
        resp.put("entryId", payload.entryId());
        resp.put("posX", payload.posX());
        resp.put("posY", payload.posY());
        resp.put("posZ", payload.posZ());
        return resp;
    }

    @PostMapping("/ticket/renew")
    public Map<String, Object> renewTicket(@RequestBody Map<String, Object> body) {
        String ticket = body == null || body.get("ticket") == null ? null : String.valueOf(body.get("ticket"));
        MigrationTicketService.TicketPayload payload = migrationTicketService.renew(ticket);
        Map<String, Object> resp = new LinkedHashMap<>();
        if (payload == null) {
            resp.put("ok", false);
            resp.put("message", "ticket invalid or expired");
            return resp;
        }
        resp.put("ok", true);
        resp.put("playerId", payload.playerId());
        resp.put("expireAtMillis", payload.expireAtMillis());
        return resp;
    }

    @PostMapping("/nodes/register")
    public Map<String, Object> registerNode(@RequestBody Map<String, Object> body) {
        String nodeId = str(body, "nodeId");
        String host = str(body, "host");
        int port = intVal(body, "port");
        List<Integer> sceneIds = sceneIds(body);
        centerSceneRegistry.registerNode(nodeId, host, port, sceneIds);
        return Map.of("ok", true, "registered", sceneIds.size());
    }

    @PostMapping("/nodes/heartbeat")
    public Map<String, Object> heartbeat(@RequestBody Map<String, Object> body) {
        String nodeId = str(body, "nodeId");
        List<Integer> sceneIds = sceneIds(body);
        centerSceneRegistry.heartbeat(nodeId, sceneIds);
        int purged = centerSceneRegistry.purgeStale(sceneRuntimeProperties.getNodeStaleMs());
        return Map.of("ok", true, "purged", purged);
    }

    @PostMapping("/nodes/unregister")
    public Map<String, Object> unregisterNode(@RequestBody Map<String, Object> body) {
        String nodeId = str(body, "nodeId");
        centerSceneRegistry.unregisterNode(nodeId);
        return Map.of("ok", true);
    }

    @GetMapping("/nodes")
    public List<Map<String, Object>> listNodes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CenterSceneRegistry.SceneServerNode n : centerSceneRegistry.listAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sceneId", n.getSceneId());
            row.put("zoneId", n.getZoneId());
            row.put("nodeId", n.getNodeId());
            row.put("host", n.getIp());
            row.put("port", n.getPort());
            row.put("lastHeartbeatMillis", n.getLastHeartbeatMillis());
            out.add(row);
        }
        return out;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return "";
        }
        return String.valueOf(body.get(key));
    }

    private static int intVal(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return 0;
        }
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> sceneIds(Map<String, Object> body) {
        if (body == null || body.get("sceneIds") == null) {
            return List.of();
        }
        Object raw = body.get("sceneIds");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) {
                out.add(n.intValue());
            } else if (o != null) {
                try {
                    out.add(Integer.parseInt(String.valueOf(o)));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        return out;
    }
}
