package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费活动奖励履约命令，写入 grant_idempotency 并实际发货，与活动服彻底解耦。
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class ActivityRewardCommandMqConsumer {

    private static final Logger log = LoggerFactory.getLogger(ActivityRewardCommandMqConsumer.class);

    private final BagService bagService;
    private final String nameServer;
    private final String topic;
    private final String group;
    private DefaultMQPushConsumer consumer;

    public ActivityRewardCommandMqConsumer(
            BagService bagService,
            @Value("${rocketmq.name-server:127.0.0.1:9876}") String nameServer,
            @Value("${activity.mq.topic:ACTIVITY_EVENTS}") String topic,
            @Value("${activity.mq.bag-reward-consumer-group:bag-activity-reward-consumer}") String group) {
        this.bagService = bagService;
        this.nameServer = nameServer;
        this.topic = topic;
        this.group = group;
    }

    @PostConstruct
    public void start() throws Exception {
        consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(topic, "reward_grant");
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                handle(new String(msg.getBody(), StandardCharsets.UTF_8));
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("ActivityRewardCommand MQ consumer started topic={} group={}", topic, group);
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    void handle(String body) {
        Map<String, String> fields = parse(body);
        long playerId = parseLong(fields.get("playerId"));
        String idem = fields.getOrDefault("idempotencyKey", "");
        String items = fields.getOrDefault("items", "");
        if (playerId <= 0 || idem.isBlank()) {
            log.warn("Skip invalid ActivityRewardCommand body={}", body);
            return;
        }
        List<ItemReward> rewards = parseItems(items);
        int rc = bagService.grantItemsIdempotent(playerId, idem, rewards);
        log.info("ActivityReward fulfilled playerId={} key={} rc={} items={}", playerId, idem, rc, rewards.size());
    }

    static List<ItemReward> parseItems(String itemsCsv) {
        List<ItemReward> list = new ArrayList<>();
        if (itemsCsv == null || itemsCsv.isBlank()) {
            return list;
        }
        // format: itemId:count,itemId:count
        for (String part : itemsCsv.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) {
                continue;
            }
            try {
                int itemId = Integer.parseInt(kv[0].trim());
                int count = Integer.parseInt(kv[1].trim());
                if (itemId > 0 && count > 0) {
                    list.add(ItemReward.newBuilder().setItemId(itemId).setCount(count).build());
                }
            } catch (NumberFormatException ignore) {
                // skip bad token
            }
        }
        return list;
    }

    private static Map<String, String> parse(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isBlank()) {
            return map;
        }
        for (String part : body.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                map.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        return map;
    }

    private static long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
