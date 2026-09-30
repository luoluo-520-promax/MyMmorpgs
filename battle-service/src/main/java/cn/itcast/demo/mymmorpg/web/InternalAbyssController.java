package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.abyss.AbyssFloorConfigLoader;
import cn.itcast.demo.mymmorpg.abyss.AbyssService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "battle-service")
@RequestMapping("/internal/battle/abyss")
public class InternalAbyssController {

    private final AbyssService abyssService;
    private final AbyssFloorConfigLoader configLoader;

    public InternalAbyssController(AbyssService abyssService, AbyssFloorConfigLoader configLoader) {
        this.abyssService = abyssService;
        this.configLoader = configLoader;
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestHeader("X-Player-Id") long playerId) {
        return abyssService.start(playerId);
    }

    @PostMapping("/chamber/finish")
    public Map<String, Object> finish(@RequestHeader("X-Player-Id") long playerId,
                                      @RequestBody Map<String, Object> body) {
        boolean cleared = Boolean.TRUE.equals(body.get("cleared"))
                || "true".equalsIgnoreCase(String.valueOf(body.getOrDefault("cleared", "false")));
        int remainHp = ((Number) body.getOrDefault("remainHp", 0)).intValue();
        int energy = ((Number) body.getOrDefault("energy", 0)).intValue();
        long remainTimeSec = ((Number) body.getOrDefault("remainTimeSec", 0)).longValue();
        @SuppressWarnings("unchecked")
        List<Integer> skillCdMs = (List<Integer>) body.get("skillCdMs");
        return abyssService.finishChamber(playerId, cleared, remainHp, energy, skillCdMs, remainTimeSec);
    }

    @GetMapping("/progress")
    public Map<String, Object> progress(@RequestHeader("X-Player-Id") long playerId) {
        return abyssService.progress(playerId);
    }

    @PostMapping("/config/reload")
    public Map<String, Object> reload() {
        configLoader.reload();
        return Map.of("ok", true, "seasonId", configLoader.current().getSeasonId());
    }

    @PostMapping("/ops/reset-player")
    public Map<String, Object> resetPlayer(@RequestHeader("X-Player-Id") long playerId) {
        abyssService.resetPlayer(playerId);
        return Map.of("ok", true);
    }
}
