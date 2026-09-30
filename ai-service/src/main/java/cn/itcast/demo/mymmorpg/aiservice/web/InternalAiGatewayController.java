package cn.itcast.demo.mymmorpg.aiservice.web;

import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/ai")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "ai-service")
public class InternalAiGatewayController {

    private final AiPlatformFacade platform;

    public InternalAiGatewayController(AiPlatformFacade platform) {
        this.platform = platform;
    }

    @PostMapping("/infer")
    public Map<String, Object> infer(@RequestBody Map<String, Object> body) {
        String model = str(body, "modelName", "rule-default");
        String task = str(body, "task", "infer");
        long playerId = (long) num(body, "playerId", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> features = body.get("features") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return platform.infer(model, task, playerId, features);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return platform.health();
    }

    @PostMapping("/cache/clear")
    public Map<String, Object> clearCache() {
        platform.gateway().clearCache();
        return Map.of("ok", true);
    }

    private static String str(Map<String, Object> body, String key, String def) {
        Object v = body == null ? null : body.get(key);
        return v == null || String.valueOf(v).isBlank() ? def : String.valueOf(v);
    }

    private static double num(Map<String, Object> body, String key, double def) {
        Object v = body == null ? null : body.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return def;
    }
}
