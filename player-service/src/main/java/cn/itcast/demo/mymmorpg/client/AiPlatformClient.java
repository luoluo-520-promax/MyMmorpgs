package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * player-service / admin-service → ai-service 内部 AI 平台 Feign 客户端。
 */
@FeignClient(name = "ai-service", contextId = "aiPlatformClient",
        configuration = InternalApiFeignConfiguration.class,
        fallbackFactory = AiPlatformClientFallbackFactory.class)
public interface AiPlatformClient {

    @PostMapping("/internal/ai/recommend/items")
    Map<String, Object> recommend(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/recommend/behavior")
    Map<String, Object> recordBehavior(@RequestBody Map<String, Object> body);

    @GetMapping("/internal/ai/recommend/profile")
    Map<String, Object> profile(@RequestParam("playerId") long playerId);

    @PostMapping("/internal/ai/support/ask")
    Map<String, Object> supportAsk(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/tactical/advise")
    Map<String, Object> tacticalAdvise(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/retention/evaluate")
    Map<String, Object> retentionEvaluate(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/content/generate")
    Map<String, Object> contentGenerate(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/content/validate")
    Map<String, Object> contentValidate(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/infer")
    Map<String, Object> infer(@RequestBody Map<String, Object> body);

    @GetMapping("/internal/ai/health")
    Map<String, Object> health();

    @PostMapping("/internal/ai/narrative/generate")
    Map<String, Object> narrativeGenerate(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/companion/dialogue")
    Map<String, Object> companionDialogue(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/analyst/feedback")
    Map<String, Object> analystFeedback(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/vision/waypoint")
    Map<String, Object> screenWaypoint(@RequestBody Map<String, Object> body);

    @PostMapping("/internal/ai/persona/style")
    Map<String, Object> personaStyle(@RequestBody Map<String, Object> body);
}
