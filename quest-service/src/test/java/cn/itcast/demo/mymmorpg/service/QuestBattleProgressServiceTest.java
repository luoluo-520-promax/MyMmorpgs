package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class QuestBattleProgressServiceTest {

    private QuestService questService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private QuestBattleProgressService service;

    @BeforeMethod
    public void setUp() {
        questService = mock(QuestService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<MqInboxService> inbox = mock(ObjectProvider.class);
        when(inbox.getIfAvailable()).thenReturn(null);
        service = new QuestBattleProgressService(questService, inbox, redis, "quest-battle-consumer");
    }

    @Test
    public void win_delegatesToQuestService() {
        when(questService.onBattleWon(5L)).thenReturn(List.of(1002));
        assertThat(service.applyBattleEnd(5L, 88L, 1)).containsExactly(1002);
        verify(questService).onBattleWon(5L);
    }

    @Test
    public void lose_skips() {
        assertThat(service.applyBattleEnd(5L, 88L, 0)).isEmpty();
        verifyNoInteractions(questService);
    }

    @Test
    public void inboxDuplicate_skips() {
        MqInboxService inbox = mock(MqInboxService.class);
        when(inbox.tryClaim(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        @SuppressWarnings("unchecked")
        ObjectProvider<MqInboxService> inboxProvider = mock(ObjectProvider.class);
        when(inboxProvider.getIfAvailable()).thenReturn(inbox);
        service = new QuestBattleProgressService(questService, inboxProvider, redis, "quest-battle-consumer");

        assertThat(service.applyBattleEnd(5L, 88L, 1)).isEmpty();
        verifyNoInteractions(questService);
    }
}
