package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.PortRemoteProperties;
import cn.itcast.demo.mymmorpg.port.SkinUnlockPort;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RestSkinUnlockPort implements SkinUnlockPort {

    private static final Logger log = LoggerFactory.getLogger(RestSkinUnlockPort.class);

    private final InternalApiRestClient restClient;
    private final PortRemoteProperties properties;

    public RestSkinUnlockPort(InternalApiRestClient restClient, PortRemoteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public int unlockByItem(long playerId, int itemId) {
        try {
            Integer rc = restClient.postJson(
                    properties.getPlayerServiceUrl() + "/internal/player/skin/unlock-by-item",
                    playerId,
                    Map.of("itemId", itemId),
                    Integer.class);
            return rc == null ? RetCode.INTERNAL_ERROR : rc;
        } catch (Exception e) {
            log.warn("远程 SkinUnlockPort.unlockByItem 失败 playerId={} itemId={}", playerId, itemId, e);
            return RetCode.INTERNAL_ERROR;
        }
    }

    @Override
    public int grantSkin(long playerId, int skinId) {
        try {
            Integer rc = restClient.postJson(
                    properties.getPlayerServiceUrl() + "/internal/player/skin/grant",
                    playerId,
                    Map.of("skinId", skinId),
                    Integer.class);
            return rc == null ? RetCode.INTERNAL_ERROR : rc;
        } catch (Exception e) {
            log.warn("远程 SkinUnlockPort.grantSkin 失败 playerId={} skinId={}", playerId, skinId, e);
            return RetCode.INTERNAL_ERROR;
        }
    }
}
