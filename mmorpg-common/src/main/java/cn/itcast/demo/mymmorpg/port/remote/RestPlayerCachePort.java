package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.PortRemoteProperties;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RestPlayerCachePort implements PlayerCachePort {

    private static final Logger log = LoggerFactory.getLogger(RestPlayerCachePort.class);

    private final InternalApiRestClient restClient;
    private final PortRemoteProperties properties;

    public RestPlayerCachePort(InternalApiRestClient restClient, PortRemoteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Player findById(long playerId) {
        try {
            return restClient.get(
                    properties.getPlayerServiceUrl() + "/internal/player/cache/" + playerId,
                    playerId, Player.class);
        } catch (Exception e) {
            log.warn("远程 PlayerCachePort.findById 失败 playerId={}", playerId, e);
            return null;
        }
    }

    @Override
    public boolean existsById(long playerId) {
        return findById(playerId) != null;
    }

    @Override
    public Player saveCacheAndMarkDirty(Player player) {
        if (player == null || player.getId() == null) {
            return player;
        }
        try {
            return restClient.postJson(
                    properties.getPlayerServiceUrl() + "/internal/player/cache/save",
                    player.getId(), player, Player.class);
        } catch (Exception e) {
            log.warn("远程 PlayerCachePort.saveCacheAndMarkDirty 失败 playerId={}", player.getId(), e);
            return player;
        }
    }
}
