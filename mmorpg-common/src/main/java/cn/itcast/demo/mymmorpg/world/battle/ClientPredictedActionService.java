package cn.itcast.demo.mymmorpg.world.battle;

import cn.itcast.demo.mymmorpg.sync.ServerShadowService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端预表现：闪避/攻击按下即播动画与位移，服务端异步校验后软回滚数值。
 */
@Service
public class ClientPredictedActionService {

    public enum PredictedAction {
        DODGE, ATTACK, GRAPPLE, AIR_DASH
    }

    private final ServerShadowService shadow;
    private final ConcurrentHashMap<String, Map<String, Object>> pending = new ConcurrentHashMap<>();

    public ClientPredictedActionService() {
        this(new ServerShadowService());
    }

    public ClientPredictedActionService(ServerShadowService shadow) {
        this.shadow = shadow == null ? new ServerShadowService() : shadow;
    }

    private static String key(long playerId, String actionId) {
        return playerId + ":" + (actionId == null ? "" : actionId.trim());
    }

    /**
     * 客户端按下瞬间调用：立即允许本地播放，无需等待 ACK。
     */
    public Map<String, Object> predictStart(
            long playerId, String actionId, PredictedAction action,
            float px, float py, float pz, float speed, long clientTs) {
        shadow.recordPosition(playerId, clientTs, px, py, pz, speed);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("event", "CLIENT_PREDICT_START");
        body.put("playerId", playerId);
        body.put("actionId", actionId);
        body.put("action", action == null ? PredictedAction.DODGE.name() : action.name());
        body.put("playAnimationImmediately", true);
        body.put("playVfxImmediately", true);
        body.put("waitServerForDamage", false);
        body.put("clientTs", clientTs);
        pending.put(key(playerId, actionId), body);
        return body;
    }

    /**
     * 服务端校验完成：通过则确认伤害；失败则软回滚（保留位移/动画）。
     */
    public Map<String, Object> reconcile(
            long playerId, String actionId, long clientTs,
            float px, float py, float pz, float speed,
            boolean hitValid, boolean cooldownValid) {
        Map<String, Object> soft = shadow.softRollbackDecision(
                playerId, clientTs, px, py, pz, speed, hitValid, cooldownValid);
        pending.remove(key(playerId, actionId));
        Map<String, Object> body = new LinkedHashMap<>(soft);
        body.put("actionId", actionId);
        body.put("event", Boolean.TRUE.equals(soft.get("rollbackDamage"))
                || Boolean.TRUE.equals(soft.get("rollbackCooldown"))
                ? PrePlaybackService.ROLLBACK : "IMPACT_CONFIRM");
        body.put("softRollback", !hitValid || !cooldownValid);
        return body;
    }

    public ServerShadowService shadow() {
        return shadow;
    }

    /**
     * 心跳携带 environmentAudioProfile，洞穴深度由 echoDelayMs 推断。
     */
    public Map<String, Object> heartbeatWithEnvironment(
            long playerId, String biome, int echoDelayMs, long nowMs) {
        Map<String, Object> hb = new LinkedHashMap<>(shadow.heartbeat(playerId, nowMs));
        hb.put("playerId", playerId);
        hb.put("environmentAudioProfile",
                "CAVE".equalsIgnoreCase(biome == null ? "" : biome) ? "CAVE_REVERB" : "OPEN_AIR");
        hb.put("echoDelayMs", Math.max(0, echoDelayMs));
        hb.put("serverEchoValidated", true);
        hb.put("caveDepthHint", echoDelayMs > 200 ? "DEEP" : echoDelayMs > 80 ? "SHALLOW" : "OPEN");
        return hb;
    }
}
