package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.quest.QuestConfigService;
import cn.itcast.demo.mymmorpg.quest.QuestTemplate;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务按事件推进：SHOP_PAY / GACHA_DRAW / BATTLE_WIN。
 */
public class QuestProgressEventFlowTest {

    @Test
    public void advanceByEvent_matchesEventType() {
        QuestTemplate shopQuest = new QuestTemplate();
        shopQuest.questId = 101;
        shopQuest.eventType = QuestProgressService.EVENT_SHOP_PAY;
        shopQuest.target = 3;

        QuestTemplate gachaQuest = new QuestTemplate();
        gachaQuest.questId = 102;
        gachaQuest.eventType = QuestProgressService.EVENT_GACHA_DRAW;
        gachaQuest.target = 10;

        QuestTemplate battleDaily = new QuestTemplate();
        battleDaily.questId = 103;
        battleDaily.questType = 3; // default BATTLE_WIN
        battleDaily.target = 1;

        QuestConfigService configs = mock(QuestConfigService.class);
        when(configs.listAll()).thenReturn(List.of(shopQuest, gachaQuest, battleDaily));
        QuestService questService = mock(QuestService.class);

        QuestProgressService progress = new QuestProgressService(questService, configs);
        assertThat(progress.advanceByEvent(9L, QuestProgressService.EVENT_SHOP_PAY, 1))
                .containsExactly(101);
        verify(questService).advanceProgress(9L, 101, 1);

        QuestGachaProgressService gacha = new QuestGachaProgressService(progress);
        assertThat(gacha.applyGachaDraw(9L, 1, 5)).containsExactly(102);
        verify(questService).advanceProgress(9L, 102, 5);

        assertThat(progress.advanceByEvent(9L, QuestProgressService.EVENT_BATTLE_WIN, 1))
                .containsExactly(103);
    }

    @Test
    public void shopPayProgressService_idempotentByRedis() {
        QuestProgressService progress = mock(QuestProgressService.class);
        when(progress.advanceByEvent(1L, QuestProgressService.EVENT_SHOP_PAY, 1))
                .thenReturn(List.of(101));

        org.springframework.data.redis.core.StringRedisTemplate redis =
                mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);

        org.springframework.beans.factory.ObjectProvider<MqInboxService> inbox =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(inbox.getIfAvailable()).thenReturn(null);

        QuestShopPayProgressService svc = new QuestShopPayProgressService(
                progress, inbox, redis, "quest-shop-consumer");
        assertThat(svc.applyShopPaid(1L, "ORD-1", 600)).containsExactly(101);

        when(ops.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(false);
        assertThat(svc.applyShopPaid(1L, "ORD-1", 600)).isEmpty();
    }
}
