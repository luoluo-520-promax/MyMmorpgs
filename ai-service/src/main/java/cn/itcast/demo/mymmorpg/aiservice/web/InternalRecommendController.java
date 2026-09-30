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
@RequestMapping("/internal/ai/recommend")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "ai-service")
public class InternalRecommendController {

    private final AiPlatformFacade platform;

    public InternalRecommendController(AiPlatformFacade platform) {
        this.platform = platform;
    }

    @PostMapping("/items")
    public Map<String, Object> recommend(@RequestBody Map<String, Object> body) {
        long playerId = ((Number) body.getOrDefault("playerId", 0L)).longValue();
        String category = body.get("category") == null ? null : String.valueOf(body.get("category"));
        int topN = body.get("topN") instanceof Number n ? n.intValue() : 5;
        return platform.recommend(playerId, category, topN);
    }

    @PostMapping("/behavior")
    public Map<String, Object> behavior(@RequestBody Map<String, Object> body) {
        long playerId = ((Number) body.getOrDefault("playerId", 0L)).longValue();
        String itemId = String.valueOf(body.getOrDefault("itemId", ""));
        String category = String.valueOf(body.getOrDefault("category", "item"));
        return platform.recordBehavior(playerId, itemId, category);
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(@RequestParam long playerId) {
        return platform.profile(playerId);
    }
}
