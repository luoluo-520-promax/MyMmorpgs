package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AuthTokenService;
import cn.itcast.demo.mymmorpg.service.PlayerAiAdvisorService;
import cn.itcast.demo.mymmorpg.service.PlayerAiPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 玩家 AI 能力 HTTP 入口：顾问 / 推荐 / 智能客服 / 实时战术。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
@RequestMapping("/api/player/ai")
public class PlayerAiController {

    private final PlayerAiAdvisorService playerAiAdvisorService;
    private final PlayerAiPlatformService aiPlatformService;
    private final AuthTokenService authTokenService;

    public PlayerAiController(PlayerAiAdvisorService playerAiAdvisorService,
                              PlayerAiPlatformService aiPlatformService,
                              AuthTokenService authTokenService) {
        this.playerAiAdvisorService = playerAiAdvisorService;
        this.aiPlatformService = aiPlatformService;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/advise")
    public ResponseEntity<Map<String, Object>> advise(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestBody Map<String, Object> body) {
        Long accountId = resolveAccountId(authorization, authToken);
        if (accountId == null) {
            throw new IllegalArgumentException("认证令牌无效或已过期");
        }
        long playerId = toLong(body == null ? null : body.get("playerId"));
        String question = body == null || body.get("question") == null ? null : String.valueOf(body.get("question"));
        String topic = body == null || body.get("topic") == null ? null : String.valueOf(body.get("topic"));
        return ResponseEntity.ok(playerAiAdvisorService.advise(accountId, playerId, question, topic));
    }

    @PostMapping("/recommend")
    public ResponseEntity<Map<String, Object>> recommend(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestBody Map<String, Object> body) {
        requireAuth(authorization, authToken);
        long playerId = toLong(body == null ? null : body.get("playerId"));
        String category = body == null || body.get("category") == null ? null : String.valueOf(body.get("category"));
        int topN = body != null && body.get("topN") instanceof Number n ? n.intValue() : 5;
        return ResponseEntity.ok(aiPlatformService.recommend(playerId, category, topN));
    }

    @PostMapping("/support")
    public ResponseEntity<Map<String, Object>> support(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestBody Map<String, Object> body) {
        requireAuth(authorization, authToken);
        long playerId = toLong(body == null ? null : body.get("playerId"));
        String question = body == null || body.get("question") == null ? "" : String.valueOf(body.get("question"));
        return ResponseEntity.ok(aiPlatformService.supportAsk(playerId, question));
    }

    @PostMapping("/tactical")
    public ResponseEntity<Map<String, Object>> tactical(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestBody Map<String, Object> body) {
        requireAuth(authorization, authToken);
        long playerId = toLong(body == null ? null : body.get("playerId"));
        return ResponseEntity.ok(aiPlatformService.tacticalAdvise(playerId, body));
    }

    @PostMapping("/analyst/feedback")
    public ResponseEntity<Map<String, Object>> analystFeedback(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestBody Map<String, Object> body) {
        requireAuth(authorization, authToken);
        long playerId = toLong(body == null ? null : body.get("playerId"));
        return ResponseEntity.ok(aiPlatformService.analystFeedback(playerId, body));
    }

    @PostMapping("/companion/dialogue")
    public ResponseEntity<Map<String, Object>> companionDialogue(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestBody Map<String, Object> body) {
        requireAuth(authorization, authToken);
        long playerId = toLong(body == null ? null : body.get("playerId"));
        return ResponseEntity.ok(aiPlatformService.companionDialogue(playerId, body));
    }

    @PostMapping("/vision/waypoint")
    public ResponseEntity<Map<String, Object>> visionWaypoint(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestBody Map<String, Object> body) {
        requireAuth(authorization, authToken);
        long playerId = toLong(body == null ? null : body.get("playerId"));
        return ResponseEntity.ok(aiPlatformService.screenWaypointHelp(playerId, body));
    }

    @PostMapping("/persona/style")
    public ResponseEntity<Map<String, Object>> personaStyle(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestBody Map<String, Object> body) {
        requireAuth(authorization, authToken);
        long playerId = toLong(body == null ? null : body.get("playerId"));
        String style = body == null || body.get("style") == null ? "DETAILED" : String.valueOf(body.get("style"));
        return ResponseEntity.ok(aiPlatformService.personaSetStyle(playerId, style));
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> profile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Auth-Token", required = false) String authToken,
            @RequestParam long playerId) {
        requireAuth(authorization, authToken);
        return ResponseEntity.ok(aiPlatformService.profile(playerId));
    }

    private void requireAuth(String authorization, String authToken) {
        if (resolveAccountId(authorization, authToken) == null) {
            throw new IllegalArgumentException("认证令牌无效或已过期");
        }
    }

    private Long resolveAccountId(String authorization, String authToken) {
        String token = null;
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = authorization.substring(7).trim();
        } else if (authToken != null && !authToken.isBlank()) {
            token = authToken.trim();
        }
        return authTokenService.getAccountIdByToken(token);
    }

    private static long toLong(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("playerId 不能为空");
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value).trim());
    }
}
