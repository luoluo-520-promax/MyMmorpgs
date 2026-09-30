package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.PortRemoteProperties;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Collection;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RestPlayerNotificationPort implements PlayerNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(RestPlayerNotificationPort.class);

    private final InternalApiRestClient restClient;
    private final PortRemoteProperties properties;

    public RestPlayerNotificationPort(InternalApiRestClient restClient, PortRemoteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public void send(long playerId, int msgId, byte[] payload) {
        try {
            restClient.postJsonVoid(
                    properties.getPlayerServiceUrl() + "/internal/player/notify/send",
                    playerId,
                    Map.of(
                            "msgId", msgId,
                            "payload", Base64.getEncoder().encodeToString(payload == null ? new byte[0] : payload)));
        } catch (Exception e) {
            log.warn("远程 PlayerNotificationPort.send 失败 playerId={} msgId={}", playerId, msgId, e);
        }
    }

    @Override
    public void broadcastAllOnline(int msgId, byte[] payload, Long excludePlayerId) {
        try {
            restClient.postJsonVoid(
                    properties.getPlayerServiceUrl() + "/internal/player/notify/broadcast",
                    0L,
                    Map.of(
                            "msgId", msgId,
                            "payload", Base64.getEncoder().encodeToString(payload == null ? new byte[0] : payload),
                            "excludePlayerId", excludePlayerId == null ? 0L : excludePlayerId));
        } catch (Exception e) {
            log.warn("远程 PlayerNotificationPort.broadcastAllOnline 失败 msgId={}", msgId, e);
        }
    }

    @Override
    public void sendToPlayers(Collection<Long> playerIds, int msgId, byte[] payload) {
        PlayerNotificationPort.super.sendToPlayers(playerIds, msgId, payload);
    }
}
