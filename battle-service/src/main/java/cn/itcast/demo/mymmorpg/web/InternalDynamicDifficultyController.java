package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.dda.DynamicDifficultyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/battle/dda")
public class InternalDynamicDifficultyController {

    private final DynamicDifficultyService dynamicDifficultyService;

    public InternalDynamicDifficultyController(DynamicDifficultyService dynamicDifficultyService) {
        this.dynamicDifficultyService = dynamicDifficultyService;
    }

    @PostMapping("/record")
    public Map<String, Object> record(@RequestHeader("X-Player-Id") long playerId,
                                      @RequestParam double dps,
                                      @RequestParam(defaultValue = "true") boolean survived,
                                      @RequestParam(defaultValue = "180000") long clearTimeMs) {
        dynamicDifficultyService.recordBattleResult(playerId, dps, survived, clearTimeMs);
        return Map.of("ok", true, "playerId", playerId);
    }

    @GetMapping("/evaluate")
    public Map<String, Object> evaluate(@RequestHeader("X-Player-Id") long playerId) {
        return dynamicDifficultyService.snapshot(playerId);
    }
}
