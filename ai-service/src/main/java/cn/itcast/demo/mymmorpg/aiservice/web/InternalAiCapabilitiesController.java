package cn.itcast.demo.mymmorpg.aiservice.web;

import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/ai")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "ai-service")
public class InternalAiCapabilitiesController {

    private final AiPlatformFacade platform;

    public InternalAiCapabilitiesController(AiPlatformFacade platform) {
        this.platform = platform;
    }

    @PostMapping("/support/ask")
    public Map<String, Object> supportAsk(@RequestBody Map<String, Object> body) {
        long playerId = ((Number) body.getOrDefault("playerId", 0L)).longValue();
        String question = String.valueOf(body.getOrDefault("question", ""));
        boolean allowTicket = !Boolean.FALSE.equals(body.get("allowTicket"));
        return platform.supportAsk(playerId, question, allowTicket);
    }

    @PostMapping("/retention/evaluate")
    public Map<String, Object> retention(@RequestBody Map<String, Object> body) {
        long playerId = ((Number) body.getOrDefault("playerId", 0L)).longValue();
        return platform.retentionEvaluate(playerId, body);
    }

    @PostMapping("/tactical/advise")
    public Map<String, Object> tactical(@RequestBody Map<String, Object> body) {
        return platform.tacticalAdvise(body);
    }

    @PostMapping("/content/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", "activity"));
        String theme = String.valueOf(body.getOrDefault("theme", "限时活动"));
        @SuppressWarnings("unchecked")
        Map<String, Object> knobs = body.get("knobs") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return platform.contentGenerate(type, theme, knobs);
    }

    @PostMapping("/content/validate")
    public Map<String, Object> validate(@RequestBody Map<String, Object> draft) {
        return platform.contentValidate(draft);
    }

    @PostMapping("/ops/activity/{activityId}/feedback")
    public Map<String, Object> activityFeedback(@PathVariable String activityId,
                                                @RequestBody Map<String, Object> body) {
        String event = String.valueOf(body.getOrDefault("event", "exposure"));
        double cost = body.get("cost") instanceof Number n ? n.doubleValue() : 0;
        return platform.activityFeedback(activityId, event, cost);
    }

    @GetMapping("/ops/activity/{activityId}/optimize")
    public Map<String, Object> activityOptimize(@PathVariable String activityId) {
        return platform.activitySnapshot(activityId);
    }

    @GetMapping("/mlops/models")
    public Map<String, Object> models() {
        return Map.of("ok", true, "models", platform.gateway().versions().snapshot());
    }

    @PostMapping("/mlops/models/register")
    public Map<String, Object> registerModel(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.get("modelName"));
        String version = String.valueOf(body.get("version"));
        String uri = String.valueOf(body.getOrDefault("artifactUri", ""));
        var mv = platform.gateway().versions().register(name, version, uri);
        return Map.of("ok", true, "modelName", mv.modelName(), "version", mv.version());
    }

    @PostMapping("/mlops/models/rollback")
    public Map<String, Object> rollback(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.get("modelName"));
        String version = String.valueOf(body.get("version"));
        return platform.gateway().versions().rollback(name, version)
                .<Map<String, Object>>map(v -> Map.of("ok", true, "modelName", v.modelName(), "version", v.version()))
                .orElse(Map.of("ok", false, "error", "version_not_found"));
    }

    @GetMapping("/mlops/drift")
    public Map<String, Object> drift(@RequestParam(defaultValue = "recommend_cf") String modelName) {
        return platform.driftSnapshot(modelName);
    }

    @GetMapping("/quota")
    public Map<String, Object> quota(@RequestParam long playerId) {
        return Map.of("playerId", playerId, "hint", "see supportAsk.llmQuota");
    }
}
