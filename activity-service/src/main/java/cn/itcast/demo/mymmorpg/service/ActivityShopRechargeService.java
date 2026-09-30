package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Activity;
import cn.itcast.demo.mymmorpg.model.ActivityTypes;
import cn.itcast.demo.mymmorpg.repository.ActivityRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 商城实付累充：HTTP 与 MQ 共用 orderId 幂等键（Inbox 优先，Redis 兜底）。
 */
@Service
public class ActivityShopRechargeService {

    private static final String IDEM_KEY = "activity:recharge:order:";

    private final ActivityRepository activityRepository;
    private final ActivityService activityService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<MqInboxService> mqInboxService;
    private final String consumerGroup;

    public ActivityShopRechargeService(ActivityRepository activityRepository,
                                       ActivityService activityService,
                                       StringRedisTemplate stringRedisTemplate,
                                       ObjectProvider<MqInboxService> mqInboxService,
                                       @Value("${shop.mq.consumer-group:activity-shop-consumer}") String consumerGroup) {
        this.activityRepository = activityRepository;
        this.activityService = activityService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mqInboxService = mqInboxService;
        this.consumerGroup = consumerGroup;
    }

    public List<Long> applyRecharge(long playerId, long delta, String orderId) {
        List<Long> updated = new ArrayList<>();
        if (playerId <= 0 || delta <= 0) {
            return updated;
        }
        if (orderId != null && !orderId.isBlank()) {
            MqInboxService inbox = mqInboxService.getIfAvailable();
            if (inbox != null) {
                boolean first = inbox.tryClaim(consumerGroup, "shop:" + orderId, "SHOP_EVENTS",
                        "playerId=" + playerId + "|amount=" + delta + "|orderId=" + orderId);
                if (!first) {
                    return updated;
                }
            } else {
                Boolean first = stringRedisTemplate.opsForValue()
                        .setIfAbsent(IDEM_KEY + orderId, "1", Duration.ofDays(30));
                if (Boolean.FALSE.equals(first)) {
                    return updated;
                }
            }
        }
        for (Activity a : activityRepository.findByOpenedTrue()) {
            if (a.getType() != null && a.getType() == ActivityTypes.FIRST_RECHARGE.getCode()) {
                activityService.addRechargeProgress(playerId, a.getId(), delta);
                updated.add(a.getId());
            }
        }
        return updated;
    }
}
