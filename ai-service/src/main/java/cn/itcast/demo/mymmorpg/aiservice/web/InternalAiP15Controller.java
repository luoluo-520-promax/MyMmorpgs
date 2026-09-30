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

/**
 * P15 生成式 AI 扩展接口：动态叙事 / 伙伴 / 战斗分析 / 截图路点 / 人格。
 */
@RestController
@RequestMapping("/internal/ai")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "ai-service")
public class InternalAiP15Controller {

    private final AiPlatformFacade platform;

    public InternalAiP15Controller(AiPlatformFacade platform) {
        this.platform = platform;
    }

    @PostMapping("/narrative/generate")
    public Map<String, Object> narrativeGenerate(@RequestBody Map<String, Object> body) {
        return platform.narrativeGenerate(body);
    }

    @PostMapping("/companion/dialogue")
    public Map<String, Object> companionDialogue(@RequestBody Map<String, Object> body) {
        return platform.companionDialogue(body);
    }

    @PostMapping("/companion/remember")
    public Map<String, Object> companionRemember(@RequestBody Map<String, Object> body) {
        return platform.companionRemember(body);
    }

    @PostMapping("/companion/battle-coach")
    public Map<String, Object> companionBattleCoach(@RequestBody Map<String, Object> body) {
        return platform.companionBattleCoach(body);
    }

    @PostMapping("/companion/passive-explore")
    public Map<String, Object> companionPassiveExplore(@RequestBody Map<String, Object> body) {
        return platform.companionPassiveExplore(body);
    }

    @GetMapping("/companion/memory")
    public Map<String, Object> companionMemory(@RequestParam long playerId) {
        return platform.companion().toMap(platform.companion().get(playerId));
    }

    @PostMapping("/analyst/feedback")
    public Map<String, Object> analystFeedback(@RequestBody Map<String, Object> body) {
        return platform.analystFeedback(body);
    }

    @PostMapping("/vision/waypoint")
    public Map<String, Object> screenWaypoint(@RequestBody Map<String, Object> body) {
        return platform.screenWaypointHelp(body);
    }

    @PostMapping("/persona/resolve")
    public Map<String, Object> personaResolve(@RequestBody Map<String, Object> body) {
        return platform.personaResolve(body);
    }

    @PostMapping("/persona/style")
    public Map<String, Object> personaStyle(@RequestBody Map<String, Object> body) {
        long playerId = body.get("playerId") instanceof Number n ? n.longValue() : 0L;
        String style = String.valueOf(body.getOrDefault("style", "DETAILED"));
        return platform.personaSetStyle(playerId, style);
    }
}
