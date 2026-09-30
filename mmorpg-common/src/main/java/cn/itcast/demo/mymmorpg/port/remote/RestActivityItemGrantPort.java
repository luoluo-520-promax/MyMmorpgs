package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.PortRemoteProperties;
import cn.itcast.demo.mymmorpg.port.ActivityItemGrantPort;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RestActivityItemGrantPort implements ActivityItemGrantPort {

    private static final Logger log = LoggerFactory.getLogger(RestActivityItemGrantPort.class);

    private final InternalApiRestClient restClient;
    private final PortRemoteProperties properties;

    public RestActivityItemGrantPort(InternalApiRestClient restClient, PortRemoteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public int grantItemsForActivity(long playerId, String idempotencyKey, List<ItemReward> rewards) {
        try {
            List<Map<String, Integer>> items = new ArrayList<>();
            if (rewards != null) {
                for (ItemReward reward : rewards) {
                    items.add(Map.of("itemId", reward.getItemId(), "count", reward.getCount()));
                }
            }
            Integer rc = restClient.postJson(
                    properties.getBagServiceUrl() + "/internal/bag/activity/grant",
                    playerId,
                    Map.of(
                            "idempotencyKey", idempotencyKey == null ? "" : idempotencyKey,
                            "rewards", items),
                    Integer.class);
            return rc == null ? -1 : rc;
        } catch (Exception e) {
            log.warn("远程 ActivityItemGrantPort.grantItemsForActivity 失败 playerId={}", playerId, e);
            return -1;
        }
    }
}
