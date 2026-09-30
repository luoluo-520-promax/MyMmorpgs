package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.port.remote.InternalApiRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 战斗结束后经内部 HTTP 投影到 activity / quest。
 * 与各服务 MQ 消费者共用 Inbox 幂等键（battle:end:{battleId}），可并存不重复计数。
 */
@Component
public class BattleEndProjectionNotifier {

    private static final Logger log = LoggerFactory.getLogger(BattleEndProjectionNotifier.class);

    private final ObjectProvider<InternalApiRestClient> restClient;
    private final String activityServiceUrl;
    private final String questServiceUrl;
    private final boolean enabled;

    public BattleEndProjectionNotifier(
            ObjectProvider<InternalApiRestClient> restClient,
            @Value("${battle.projection.activity-url:http://127.0.0.1:8992}")
            String activityServiceUrl,
            @Value("${battle.projection.quest-url:http://127.0.0.1:8987}")
            String questServiceUrl,
            @Value("${battle.projection.enabled:true}") boolean enabled) {
        this.restClient = restClient;
        this.activityServiceUrl = trimSlash(activityServiceUrl);
        this.questServiceUrl = trimSlash(questServiceUrl);
        this.enabled = enabled;
    }

    public void notifyBattleEnded(long playerId, long battleId, int result) {
        if (!enabled || playerId <= 0 || battleId <= 0) {
            return;
        }
        InternalApiRestClient client = restClient.getIfAvailable();
        if (client == null) {
            return;
        }
        Map<String, Object> body = Map.of("battleId", battleId, "result", result);
        try {
            client.postJson(activityServiceUrl + "/internal/activity/battle-end", playerId, body, Map.class);
        } catch (Exception e) {
            log.warn("activity battle-end projection failed playerId={} battleId={}", playerId, battleId, e);
        }
        try {
            client.postJson(questServiceUrl + "/internal/quest/battle-end", playerId, body, Map.class);
        } catch (Exception e) {
            log.warn("quest battle-end projection failed playerId={} battleId={}", playerId, battleId, e);
        }
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
