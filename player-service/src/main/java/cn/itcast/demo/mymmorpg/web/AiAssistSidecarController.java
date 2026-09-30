package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.config.PlayerAiProperties;
import cn.itcast.demo.mymmorpg.service.PlayerAiAdvisorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 助手 sidecar 兼容入口（对齐 MyLunarCore ai-assist-service）：
 * {@code GET /ai/health}、{@code POST /internal/ai/ask}；默认 rule-coach，LLM 关闭时可回退规则建议。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
public class AiAssistSidecarController {

    private final PlayerAiAdvisorService playerAiAdvisorService;
    private final PlayerAiProperties properties;

    public AiAssistSidecarController(PlayerAiAdvisorService playerAiAdvisorService,
                                     PlayerAiProperties properties) {
        this.playerAiAdvisorService = playerAiAdvisorService;
        this.properties = properties;
    }

    @GetMapping({"/ai/health", "/actuator/health-alias"})
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", properties.isEnabled() ? "UP" : "DOWN");
        body.put("mode", properties.getMode());
        body.put("llmEnabled", properties.isLlmEnabled());
        body.put("promptVersion", properties.getPromptVersion());
        return body;
    }

    /**
     * Body: { "accountId": 1, "playerId": 1, "question": "...", "topic": "BATTLE" }
     * Headers: X-Player-Id / X-Account-Id 可覆盖 body（便于网关透传）。
     */
    @PostMapping("/internal/ai/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestHeader(value = "X-Player-Id", required = false) Long headerPlayerId,
            @RequestHeader(value = "X-Account-Id", required = false) Long headerAccountId,
            @RequestBody Map<String, Object> body) {
        long accountId = headerAccountId != null ? headerAccountId : toLong(body, "accountId");
        long playerId = headerPlayerId != null ? headerPlayerId : toLong(body, "playerId");
        String question = body == null || body.get("question") == null ? null : String.valueOf(body.get("question"));
        String topic = body == null || body.get("topic") == null ? null : String.valueOf(body.get("topic"));
        Map<String, Object> advice = playerAiAdvisorService.advise(accountId, playerId, question, topic);
        Map<String, Object> resp = new LinkedHashMap<>(advice);
        resp.putIfAbsent("source", properties.getMode());
        return ResponseEntity.ok(resp);
    }

    private static long toLong(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        Object value = body.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value).trim());
    }
}
