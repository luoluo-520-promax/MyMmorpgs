package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.ai.platform.AiPlatformFacade;
import cn.itcast.demo.mymmorpg.client.AiPlatformClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家侧 AI 平台路由：单体走本地 AiPlatformFacade，微服务走 Feign 调 ai-service。
 */
@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "player-service")
public class PlayerAiPlatformService {

    private final boolean remoteEnabled;
    private final ObjectProvider<AiPlatformFacade> local;
    private final ObjectProvider<AiPlatformClient> remote;

    public PlayerAiPlatformService(
            @Value("${game.ai.remote.enabled:false}") boolean remoteEnabled,
            ObjectProvider<AiPlatformFacade> local,
            ObjectProvider<AiPlatformClient> remote) {
        this.remoteEnabled = remoteEnabled;
        this.local = local;
        this.remote = remote;
    }

    public Map<String, Object> recommend(long playerId, String category, int topN) {
        if (useRemote()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("playerId", playerId);
            body.put("category", category);
            body.put("topN", topN);
            return remote.getObject().recommend(body);
        }
        return local.getObject().recommend(playerId, category, topN);
    }

    public Map<String, Object> supportAsk(long playerId, String question) {
        if (useRemote()) {
            return remote.getObject().supportAsk(Map.of(
                    "playerId", playerId,
                    "question", question == null ? "" : question,
                    "allowTicket", true));
        }
        return local.getObject().supportAsk(playerId, question, true);
    }

    public Map<String, Object> tacticalAdvise(long playerId, Map<String, Object> battlefield) {
        Map<String, Object> body = battlefield == null ? new LinkedHashMap<>() : new LinkedHashMap<>(battlefield);
        body.put("playerId", playerId);
        if (useRemote()) {
            return remote.getObject().tacticalAdvise(body);
        }
        return local.getObject().tacticalAdvise(body);
    }

    public Map<String, Object> analystFeedback(long playerId, Map<String, Object> body) {
        Map<String, Object> req = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        req.put("playerId", playerId);
        if (useRemote()) {
            return remote.getObject().analystFeedback(req);
        }
        return local.getObject().analystFeedback(req);
    }

    public Map<String, Object> companionDialogue(long playerId, Map<String, Object> body) {
        Map<String, Object> req = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        req.put("playerId", playerId);
        if (useRemote()) {
            return remote.getObject().companionDialogue(req);
        }
        return local.getObject().companionDialogue(req);
    }

    public Map<String, Object> screenWaypointHelp(long playerId, Map<String, Object> body) {
        Map<String, Object> req = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        req.put("playerId", playerId);
        if (useRemote()) {
            return remote.getObject().screenWaypoint(req);
        }
        return local.getObject().screenWaypointHelp(req);
    }

    public Map<String, Object> personaSetStyle(long playerId, String style) {
        if (useRemote()) {
            return remote.getObject().personaStyle(Map.of("playerId", playerId, "style", style == null ? "DETAILED" : style));
        }
        return local.getObject().personaSetStyle(playerId, style);
    }

    public Map<String, Object> profile(long playerId) {
        if (useRemote()) {
            return remote.getObject().profile(playerId);
        }
        return local.getObject().profile(playerId);
    }

    private boolean useRemote() {
        return remoteEnabled && remote.getIfAvailable() != null;
    }
}
