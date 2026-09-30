package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.PortRemoteProperties;
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RestPlayerDataLoadPort implements PlayerDataLoadPort {

    private static final Logger log = LoggerFactory.getLogger(RestPlayerDataLoadPort.class);

    private final InternalApiRestClient restClient;
    private final PortRemoteProperties properties;

    public RestPlayerDataLoadPort(InternalApiRestClient restClient, PortRemoteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public boolean isReady(long playerId, DataType type) {
        try {
            Boolean ready = restClient.get(
                    properties.getPlayerServiceUrl() + "/internal/player/data-load/"
                            + playerId + "/" + type.name(),
                    playerId, Boolean.class);
            return Boolean.TRUE.equals(ready);
        } catch (Exception e) {
            log.warn("远程 PlayerDataLoadPort.isReady 失败 playerId={} type={}", playerId, type, e);
            return true;
        }
    }
}
