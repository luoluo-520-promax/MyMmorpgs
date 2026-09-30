package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.PortRemoteProperties;
import cn.itcast.demo.mymmorpg.port.MailItemGrantPort;
import cn.itcast.demo.mymmorpg.protocol.BagRetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "game.port.remote.enabled", havingValue = "true")
public class RestMailItemGrantPort implements MailItemGrantPort {

    private static final Logger log = LoggerFactory.getLogger(RestMailItemGrantPort.class);

    private final InternalApiRestClient restClient;
    private final PortRemoteProperties properties;

    public RestMailItemGrantPort(InternalApiRestClient restClient, PortRemoteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public int grantItemsForMail(long playerId, long mailId, List<ItemReward> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return BagRetCode.OK;
        }
        try {
            List<Map<String, Integer>> items = new ArrayList<>();
            for (ItemReward reward : rewards) {
                items.add(Map.of("itemId", reward.getItemId(), "count", reward.getCount()));
            }
            Map<String, Object> body = new HashMap<>();
            body.put("idempotencyKey", "mail:" + mailId);
            body.put("rewards", items);
            Integer rc = restClient.postJson(
                    properties.getBagServiceUrl() + "/internal/bag/grant",
                    playerId,
                    body,
                    Integer.class);
            return rc == null ? -1 : rc;
        } catch (Exception e) {
            log.warn("远程 MailItemGrantPort 失败 playerId={} mailId={}", playerId, mailId, e);
            return -1;
        }
    }
}
