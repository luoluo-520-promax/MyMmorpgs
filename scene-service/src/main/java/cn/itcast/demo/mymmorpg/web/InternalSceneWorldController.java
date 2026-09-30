package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AoiStressBenchmark;
import cn.itcast.demo.mymmorpg.service.SceneActorService;
import cn.itcast.demo.mymmorpg.world.WorldZoneManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大世界运维面：动态域统计、AOI 压测基准。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "scene-service")
@RequestMapping("/internal/scene/world")
public class InternalSceneWorldController {

    private final SceneActorService sceneActorService;
    private final AoiStressBenchmark aoiStressBenchmark;

    public InternalSceneWorldController(SceneActorService sceneActorService,
                                        AoiStressBenchmark aoiStressBenchmark) {
        this.sceneActorService = sceneActorService;
        this.aoiStressBenchmark = aoiStressBenchmark;
    }

    @GetMapping("/zones")
    public Map<String, Object> zones(@RequestParam(defaultValue = "1") int worldId) {
        Map<String, Object> body = new LinkedHashMap<>(sceneActorService.worldZoneStats(worldId));
        body.put("zones", sceneActorService.worldZoneManager().listZones(worldId));
        return body;
    }

    @PostMapping("/zones/configure")
    public Map<String, Object> configure(
            @RequestParam(defaultValue = "80") int splitThreshold,
            @RequestParam(defaultValue = "20") int mergeThreshold,
            @RequestParam(defaultValue = "8") int defaultCellSpan) {
        WorldZoneManager mgr = sceneActorService.worldZoneManager();
        mgr.configure(splitThreshold, mergeThreshold, defaultCellSpan);
        return Map.of("ok", true, "splitThreshold", splitThreshold,
                "mergeThreshold", mergeThreshold, "defaultCellSpan", defaultCellSpan);
    }

    @PostMapping("/aoi/stress")
    public Map<String, Object> aoiStress(
            @RequestParam(defaultValue = "2000") int entityCount,
            @RequestParam(defaultValue = "5000") int queryCount,
            @RequestParam(defaultValue = "100") int gridSize,
            @RequestParam(defaultValue = "300") float radius) {
        return aoiStressBenchmark.run(entityCount, queryCount, gridSize, radius);
    }
}
