package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.PlayerAiAdvisorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部 AI 顾问入口：由已鉴权网关传入 X-Player-Id 与 accountId。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
@RequestMapping("/internal/player/ai")
public class InternalPlayerAiController {

    private final PlayerAiAdvisorService playerAiAdvisorService;

    public InternalPlayerAiController(PlayerAiAdvisorService playerAiAdvisorService) {
        this.playerAiAdvisorService = playerAiAdvisorService;
    }

    /**
     * POST /internal/player/ai/advise
     * Headers: X-Player-Id, X-Account-Id
     * Body: { "question": "...", "topic": "BATTLE" }
     */
    @PostMapping("/advise")
    public ResponseEntity<Map<String, Object>> advise(
            @RequestHeader("X-Player-Id") long playerId,
            @RequestHeader("X-Account-Id") long accountId,
            @RequestBody Map<String, Object> body) {
        String question = body == null || body.get("question") == null ? null : String.valueOf(body.get("question"));
        String topic = body == null || body.get("topic") == null ? null : String.valueOf(body.get("topic"));
        return ResponseEntity.ok(playerAiAdvisorService.advise(accountId, playerId, question, topic));
    }
}
