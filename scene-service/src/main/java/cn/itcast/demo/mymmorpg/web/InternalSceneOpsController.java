package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.SceneActorService;
import cn.itcast.demo.mymmorpg.world.scene.K8sSceneAllocator;
import cn.itcast.demo.mymmorpg.world.scene.SceneBackgroundPrecreator;
import cn.itcast.demo.mymmorpg.world.scene.SceneHotMigrateService;
import cn.itcast.demo.mymmorpg.world.scene.SceneInstancePool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景弹性编排 / 热迁移 / 后台预创建 / AOI 调试可视化。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service")
@RequestMapping("/internal/scene")
public class InternalSceneOpsController {

    private final SceneActorService sceneActorService;
    private final SceneInstancePool sceneInstancePool;
    private final K8sSceneAllocator k8sSceneAllocator;
    private final SceneHotMigrateService sceneHotMigrateService;
    private final SceneBackgroundPrecreator sceneBackgroundPrecreator;

    public InternalSceneOpsController(
            SceneActorService sceneActorService,
            ObjectProvider<SceneInstancePool> sceneInstancePool,
            ObjectProvider<K8sSceneAllocator> k8sSceneAllocator,
            ObjectProvider<SceneHotMigrateService> sceneHotMigrateService,
            ObjectProvider<SceneBackgroundPrecreator> sceneBackgroundPrecreator) {
        this.sceneActorService = sceneActorService;
        this.sceneInstancePool = sceneInstancePool.getIfAvailable() != null
                ? sceneInstancePool.getIfAvailable() : new SceneInstancePool();
        this.k8sSceneAllocator = k8sSceneAllocator.getIfAvailable() != null
                ? k8sSceneAllocator.getIfAvailable()
                : new K8sSceneAllocator(this.sceneInstancePool);
        this.sceneHotMigrateService = sceneHotMigrateService.getIfAvailable() != null
                ? sceneHotMigrateService.getIfAvailable()
                : sceneActorService.sceneHotMigrateService();
        this.sceneBackgroundPrecreator = sceneBackgroundPrecreator.getIfAvailable() != null
                ? sceneBackgroundPrecreator.getIfAvailable()
                : new SceneBackgroundPrecreator(this.k8sSceneAllocator, null, null);
    }

    @GetMapping("/debug/grid")
    public Map<String, Object> aoiGrid(
            @RequestParam int sceneId,
            @RequestParam(defaultValue = "1") int lineId) {
        return sceneActorService.debugAoiGrid(sceneId, lineId);
    }

    @GetMapping("/debug/trace")
    public Map<String, Object> entityTrace(@RequestParam long entityId) {
        return sceneActorService.debugEntityTrace(entityId);
    }

    @PostMapping("/line/party-pull")
    public Map<String, Object> partyPull(
            @RequestParam long leaderId,
            @RequestParam List<Long> memberIds) {
        return sceneActorService.pullPartyToLeaderLine(leaderId, memberIds);
    }

    @PostMapping("/pool/allocate")
    public Map<String, Object> allocate(
            @RequestParam int sceneTemplateId,
            @RequestParam(defaultValue = "40") int capacity,
            @RequestParam(defaultValue = "60000") long ttlMs) {
        return k8sSceneAllocator.allocateOnBestNode(
                sceneTemplateId, capacity, ttlMs, System.currentTimeMillis());
    }

    @PostMapping("/pool/heartbeat")
    public Map<String, Object> heartbeat(@RequestParam String instanceId) {
        var inst = sceneInstancePool.heartbeat(instanceId);
        return inst == null
                ? Map.of("ok", false, "error", "not_found")
                : Map.of("ok", true, "instanceId", inst.instanceId(), "status", inst.status().name(),
                "expireAtMs", inst.expireAtMs());
    }

    @PostMapping("/pool/release")
    public Map<String, Object> release(@RequestParam String instanceId) {
        return Map.of("ok", sceneInstancePool.release(instanceId), "instanceId", instanceId);
    }

    @GetMapping("/pool/stats")
    public Map<String, Object> poolStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("allocator", k8sSceneAllocator.stats());
        m.put("pool", sceneInstancePool.stats());
        return m;
    }

    @PostMapping("/pool/node/heartbeat")
    public Map<String, Object> nodeHeartbeat(@RequestBody Map<String, Object> body) {
        long now = System.currentTimeMillis();
        k8sSceneAllocator.registerOrHeartbeat(new K8sSceneAllocator.SceneNode(
                String.valueOf(body.getOrDefault("nodeId", "scene-local")),
                String.valueOf(body.getOrDefault("host", "127.0.0.1")),
                asInt(body.get("port"), 8082),
                asInt(body.get("cpuMillis"), 200),
                asInt(body.get("memoryMb"), 512),
                asInt(body.get("playerCount"), 0),
                asInt(body.get("maxPlayers"), 200),
                !Boolean.FALSE.equals(body.get("ready")),
                now), now);
        return Map.of("ok", true, "nodes", k8sSceneAllocator.listNodes().size());
    }

    @PostMapping("/migrate/zone")
    public Map<String, Object> migrateZone(@RequestBody Map<String, Object> body) {
        int sceneId = asInt(body.get("sceneId"), 1);
        int zoneId = asInt(body.get("zoneId"), 1);
        int lineId = asInt(body.get("lineId"), 1);
        @SuppressWarnings("unchecked")
        List<Long> playerIds = body.get("playerIds") instanceof List<?> list
                ? list.stream().map(o -> ((Number) o).longValue()).toList()
                : List.of();
        Map<Long, float[]> positions = new LinkedHashMap<>();
        for (Long pid : playerIds) {
            positions.put(pid, new float[]{0f, 0f, 0f});
        }
        var plan = sceneHotMigrateService.beginZoneMigrate(
                sceneId, zoneId, lineId,
                String.valueOf(body.getOrDefault("fromNodeId", "scene-a")),
                String.valueOf(body.getOrDefault("toNodeId", "scene-b")),
                String.valueOf(body.getOrDefault("toHost", "127.0.0.1")),
                asInt(body.get("toPort"), 8082),
                playerIds, positions, List.of(), System.currentTimeMillis());
        sceneHotMigrateService.markDraining(plan.planId());
        return sceneHotMigrateService.toView(plan);
    }

    @PostMapping("/prep/cross-dungeon")
    public Map<String, Object> prepCrossDungeon(@RequestBody Map<String, Object> body) {
        int sceneId = asInt(body.get("sceneId"), 9101);
        int lineId = asInt(body.get("lineId"), 1);
        int capacity = asInt(body.get("capacity"), 4);
        @SuppressWarnings("unchecked")
        List<Long> playerIds = body.get("playerIds") instanceof List<?> list
                ? list.stream().map(o -> ((Number) o).longValue()).toList()
                : List.of();
        var handle = sceneBackgroundPrecreator.prepareCrossDungeon(
                sceneId, lineId, capacity, playerIds, System.currentTimeMillis());
        return sceneBackgroundPrecreator.toClientHandoff(handle);
    }

    @PostMapping("/prep/consume")
    public Map<String, Object> consumePrep(@RequestParam String prepId) {
        var handle = sceneBackgroundPrecreator.consume(prepId, System.currentTimeMillis());
        return handle == null
                ? Map.of("ok", false, "error", "not_found")
                : sceneBackgroundPrecreator.toClientHandoff(handle);
    }

    private static int asInt(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }
}
