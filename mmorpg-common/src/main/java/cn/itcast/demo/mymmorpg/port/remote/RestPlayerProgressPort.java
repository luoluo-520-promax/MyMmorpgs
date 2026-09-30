package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.PortRemoteProperties;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RestPlayerProgressPort implements PlayerProgressPort {

    private static final Logger log = LoggerFactory.getLogger(RestPlayerProgressPort.class);

    private final InternalApiRestClient restClient;
    private final PortRemoteProperties properties;

    public RestPlayerProgressPort(InternalApiRestClient restClient, PortRemoteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Player addExp(Player player, int expReward) {
        if (player == null || player.getId() == null) {
            return player;
        }
        try {
            return restClient.postJson(
                    properties.getPlayerServiceUrl() + "/internal/player/progress/add-exp",
                    player.getId(),
                    Map.of("player", player, "expReward", expReward),
                    Player.class);
        } catch (Exception e) {
            log.warn("远程 PlayerProgressPort.addExp 失败 playerId={} exp={}", player.getId(), expReward, e);
            return player;
        }
    }

    @Override
    public Player spendGold(Player player, long amount) {
        if (player == null || player.getId() == null) {
            return player;
        }
        try {
            return restClient.postJson(
                    properties.getPlayerServiceUrl() + "/internal/player/progress/spend-gold",
                    player.getId(),
                    Map.of("player", player, "amount", amount),
                    Player.class);
        } catch (Exception e) {
            log.warn("远程 PlayerProgressPort.spendGold 失败 playerId={} amount={}", player.getId(), amount, e);
            return player;
        }
    }

    @Override
    public Player addGold(Player player, long amount) {
        if (player == null || player.getId() == null) {
            return player;
        }
        try {
            return restClient.postJson(
                    properties.getPlayerServiceUrl() + "/internal/player/progress/add-gold",
                    player.getId(),
                    Map.of("player", player, "amount", amount),
                    Player.class);
        } catch (Exception e) {
            log.warn("远程 PlayerProgressPort.addGold 失败 playerId={} amount={}", player.getId(), amount, e);
            return player;
        }
    }
}
