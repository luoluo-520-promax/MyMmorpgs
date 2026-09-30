package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SessionLoginProperties;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * 登录踢线：按 login_policy 决策；ResumeScene 保护期内同设备仅续期不踢。
 */
@Service
@EnableConfigurationProperties(SessionLoginProperties.class)
public class SessionKickService {

    public static final int REASON_DUPLICATE_LOGIN = 1;
    public static final int REASON_KICK_OLDEST = 6;

    private final PlayerSessionService playerSessionService;
    private final PlayerNotificationPort playerNotificationPort;
    private final SceneCommandGateway sceneCommandGateway;
    private final SessionLoginProperties sessionLoginProperties;
    private final ObjectProvider<ReconnectGraceQuery> reconnectGraceQuery;

    public SessionKickService(PlayerSessionService playerSessionService,
                              PlayerNotificationPort playerNotificationPort,
                              SceneCommandGateway sceneCommandGateway,
                              SessionLoginProperties sessionLoginProperties,
                              ObjectProvider<ReconnectGraceQuery> reconnectGraceQuery) {
        this.playerSessionService = playerSessionService;
        this.playerNotificationPort = playerNotificationPort;
        this.sceneCommandGateway = sceneCommandGateway;
        this.sessionLoginProperties = sessionLoginProperties == null ? new SessionLoginProperties() : sessionLoginProperties;
        this.reconnectGraceQuery = reconnectGraceQuery;
    }

    /** 单测便捷构造。 */
    public SessionKickService(PlayerSessionService playerSessionService,
                              PlayerNotificationPort playerNotificationPort,
                              SceneCommandGateway sceneCommandGateway) {
        this(playerSessionService, playerNotificationPort, sceneCommandGateway,
                new SessionLoginProperties(), null);
    }

    /**
     * 账号重新登录时：按策略踢旧连接。同设备且处于重连保护期时仅续期。
     *
     * @return true 表示执行了踢线；false 表示跳过（重连保护）
     */
    public boolean kickPreviousSession(long accountId, String deviceId) {
        if (accountId <= 0) {
            return false;
        }
        Long playerId = playerSessionService.findBoundPlayerId(accountId);
        if (playerId == null || playerId <= 0) {
            return false;
        }
        if (shouldSkipKickForReconnect(playerId, deviceId)) {
            playerSessionService.touchHeartbeat(playerId);
            return false;
        }
        playerNotificationPort.kick(playerId, REASON_DUPLICATE_LOGIN, "账号在其他设备登录");
        sceneCommandGateway.onPlayerLeave(playerId);
        playerSessionService.markOffline(playerId);
        playerSessionService.clearAccountPlayer(accountId);
        return true;
    }

    /** 兼容旧调用。 */
    public void kickPreviousSession(long accountId) {
        kickPreviousSession(accountId, null);
    }

    public void kickPlayer(long playerId, int reason, String message) {
        if (playerId <= 0) {
            return;
        }
        playerNotificationPort.kick(playerId, reason, message == null ? "" : message);
        sceneCommandGateway.onPlayerLeave(playerId);
        playerSessionService.markOffline(playerId);
    }

    private boolean shouldSkipKickForReconnect(long playerId, String deviceId) {
        long grace = sessionLoginProperties.getReconnectNoKickMs();
        if (grace <= 0) {
            return false;
        }
        ReconnectGraceQuery q = reconnectGraceQuery == null ? null : reconnectGraceQuery.getIfAvailable();
        if (q == null) {
            return false;
        }
        if (!q.hasActiveReconnect(playerId)) {
            return false;
        }
        if (deviceId == null || deviceId.isBlank()) {
            return true;
        }
        var fields = playerSessionService.getOnlineFields(playerId);
        String bound = fields.get("device_id");
        return bound == null || bound.isBlank() || bound.equals(deviceId);
    }

    /** 由 scene 模块提供：是否处于断线重连宽限期。 */
    @FunctionalInterface
    public interface ReconnectGraceQuery {
        boolean hasActiveReconnect(long playerId);
    }
}
