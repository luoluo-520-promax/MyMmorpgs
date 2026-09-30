package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 商城支付成功 → 任务进度投影（Inbox / Redis 幂等）。
 */
@Service
public class QuestShopPayProgressService {

    private static final String REDIS_IDEM = "quest:shop:paid:";

    private final QuestProgressService questProgressService;
    private final ObjectProvider<MqInboxService> mqInboxService;
    private final StringRedisTemplate stringRedisTemplate;
    private final String consumerGroup;

    public QuestShopPayProgressService(QuestProgressService questProgressService,
                                       ObjectProvider<MqInboxService> mqInboxService,
                                       StringRedisTemplate stringRedisTemplate,
                                       @Value("${shop.mq.quest-consumer-group:quest-shop-consumer}")
                                       String consumerGroup) {
        this.questProgressService = questProgressService;
        this.mqInboxService = mqInboxService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.consumerGroup = consumerGroup;
    }

    public List<Integer> applyShopPaid(long playerId, String orderId, long amount) {
        if (playerId <= 0 || orderId == null || orderId.isBlank()) {
            return Collections.emptyList();
        }
        String messageKey = "shop:paid:" + orderId;
        MqInboxService inbox = mqInboxService.getIfAvailable();
        if (inbox != null) {
            boolean first = inbox.tryClaim(consumerGroup, messageKey, "SHOP_EVENTS",
                    "playerId=" + playerId + "|orderId=" + orderId + "|amount=" + amount);
            if (!first) {
                return Collections.emptyList();
            }
        } else {
            Boolean first = stringRedisTemplate.opsForValue()
                    .setIfAbsent(REDIS_IDEM + consumerGroup + ":" + orderId, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) {
                return Collections.emptyList();
            }
        }
        return questProgressService.advanceByEvent(playerId, QuestProgressService.EVENT_SHOP_PAY, 1);
    }
}
