package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.WorldEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 大世界事件 / 世界 BOSS 内部 API。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
@RequestMapping("/internal/activity/world-events")
public class InternalWorldEventController {

    private final WorldEventService worldEventService;

    public InternalWorldEventController(WorldEventService worldEventService) {
        this.worldEventService = worldEventService;
    }

    @PostMapping("/schedule")
    public Map<String, Object> schedule(
            @RequestParam String eventId,
            @RequestParam(defaultValue = "WORLD_BOSS") String type,
            @RequestParam(defaultValue = "1") int sceneId,
            @RequestParam(defaultValue = "0") float posX,
            @RequestParam(defaultValue = "0") float posZ,
            @RequestParam(defaultValue = "0") long startAtMs,
            @RequestParam(defaultValue = "0") long endAtMs,
            @RequestParam(defaultValue = "1000000") long bossHpMax) {
        WorldEventService.EventType eventType = WorldEventService.EventType.valueOf(type);
        return worldEventService.toView(worldEventService.schedule(
                eventId, eventType, sceneId, posX, posZ, startAtMs, endAtMs, bossHpMax));
    }

    @PostMapping("/activate")
    public Map<String, Object> activate(@RequestParam String eventId) {
        return worldEventService.toView(worldEventService.activate(eventId));
    }

    @PostMapping("/damage")
    public Map<String, Object> damage(@RequestParam String eventId,
                                      @RequestHeader("X-Player-Id") long playerId,
                                      @RequestParam long damage) {
        return worldEventService.toView(worldEventService.reportDamage(eventId, playerId, damage));
    }

    @PostMapping("/assist")
    public Map<String, Object> assist(@RequestParam String eventId,
                                      @RequestHeader("X-Player-Id") long playerId,
                                      @RequestParam(defaultValue = "0") long heal,
                                      @RequestParam(defaultValue = "0") long shield) {
        long score = worldEventService.reportAssist(eventId, playerId, heal, shield);
        return Map.of("ok", true, "eventId", eventId, "playerId", playerId, "assistScore", score);
    }

    @PostMapping("/settle")
    public Map<String, Object> settle(@RequestParam String eventId,
                                      @RequestParam(defaultValue = "10") int topN) {
        return worldEventService.settle(eventId, topN);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return worldEventService.listActive().stream().map(worldEventService::toView).toList();
    }
}
