package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 战斗结束 → 日常任务进度投影（Inbox 优先，Redis 兜底幂等）。
 */
@Service
public class QuestBattleProgressService {

    private static final String REDIS_IDEM = "quest:battle:end:";

    private final QuestService questService;
    private final ObjectProvider<MqInboxService> mqInboxService;
    private final StringRedisTemplate stringRedisTemplate;
    private final String consumerGroup;

    public QuestBattleProgressService(QuestService questService,
                                      ObjectProvider<MqInboxService> mqInboxService,
                                      StringRedisTemplate stringRedisTemplate,
                                      @Value("${battle.mq.quest-consumer-group:quest-battle-consumer}")
                                      String consumerGroup) {
        this.questService = questService;
        this.mqInboxService = mqInboxService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.consumerGroup = consumerGroup;
    }

    public List<Integer> applyBattleEnd(long playerId, long battleId, int result) {
        if (playerId <= 0 || result != 1) {
            return Collections.emptyList();
        }
        String messageKey = "battle:end:" + battleId;
        MqInboxService inbox = mqInboxService.getIfAvailable();
        if (inbox != null) {
            boolean first = inbox.tryClaim(consumerGroup, messageKey, "BATTLE_EVENTS",
                    "playerId=" + playerId + "|battleId=" + battleId + "|result=" + result);
            if (!first) {
                return Collections.emptyList();
            }
        } else {
            Boolean first = stringRedisTemplate.opsForValue()
                    .setIfAbsent(REDIS_IDEM + consumerGroup + ":" + battleId, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) {
                return Collections.emptyList();
            }
        }
        return questService.onBattleWon(playerId);
    }
}
