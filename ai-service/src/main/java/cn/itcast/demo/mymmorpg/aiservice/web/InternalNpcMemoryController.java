package cn.itcast.demo.mymmorpg.aiservice.web;

import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/ai/npc")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "ai-service")
public class InternalNpcMemoryController {

    private final AiPlatformFacade platform;

    public InternalNpcMemoryController(AiPlatformFacade platform) {
        this.platform = platform;
    }

    @GetMapping("/bond")
    public Map<String, Object> bond(@RequestParam long playerId, @RequestParam(defaultValue = "guide") String npcId) {
        return platform.npcBond(playerId, npcId);
    }

    @PostMapping("/interact")
    public Map<String, Object> interact(@RequestBody Map<String, Object> body) {
        long playerId = ((Number) body.getOrDefault("playerId", 0L)).longValue();
        String npcId = String.valueOf(body.getOrDefault("npcId", "guide"));
        String kind = String.valueOf(body.getOrDefault("kind", "DIALOGUE_POSITIVE"));
        double intensity = body.get("intensity") instanceof Number n ? n.doubleValue() : 0.4;
        String summary = String.valueOf(body.getOrDefault("summary", ""));
        return platform.npcInteract(playerId, npcId, kind, summary, intensity);
    }
}
