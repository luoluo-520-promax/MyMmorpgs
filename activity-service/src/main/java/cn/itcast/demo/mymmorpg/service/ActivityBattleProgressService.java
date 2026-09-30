package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Activity;
import cn.itcast.demo.mymmorpg.repository.ActivityRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 战斗结束 → 开启中活动的胜利次数投影（Inbox 优先，Redis 兜底幂等）。
 */
@Service
public class ActivityBattleProgressService {

    private static final String REDIS_IDEM = "activity:battle:end:";

    private final ActivityRepository activityRepository;
    private final ActivityService activityService;
    private final ObjectProvider<MqInboxService> mqInboxService;
    private final StringRedisTemplate stringRedisTemplate;
    private final String consumerGroup;

    public ActivityBattleProgressService(ActivityRepository activityRepository,
                                         ActivityService activityService,
                                         ObjectProvider<MqInboxService> mqInboxService,
                                         StringRedisTemplate stringRedisTemplate,
                                         @Value("${battle.mq.activity-consumer-group:activity-battle-consumer}")
                                         String consumerGroup) {
        this.activityRepository = activityRepository;
        this.activityService = activityService;
        this.mqInboxService = mqInboxService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.consumerGroup = consumerGroup;
    }

    /**
     * @param result 0 败 1 胜 2 平/逃
     * @return 更新过进度的活动 ID 列表
     */
    public List<Long> applyBattleEnd(long playerId, long battleId, int result) {
        List<Long> updated = new ArrayList<>();
        if (playerId <= 0 || result != 1) {
            return updated;
        }
        String messageKey = "battle:end:" + battleId;
        MqInboxService inbox = mqInboxService.getIfAvailable();
        if (inbox != null) {
            boolean first = inbox.tryClaim(consumerGroup, messageKey, "BATTLE_EVENTS",
                    "playerId=" + playerId + "|battleId=" + battleId + "|result=" + result);
            if (!first) {
                return updated;
            }
        } else {
            Boolean first = stringRedisTemplate.opsForValue()
                    .setIfAbsent(REDIS_IDEM + consumerGroup + ":" + battleId, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) {
                return updated;
            }
        }
        for (Activity a : activityRepository.findByOpenedTrue()) {
            activityService.addBattleWinProgress(playerId, a.getId(), 1);
            updated.add(a.getId());
        }
        return updated;
    }
}
