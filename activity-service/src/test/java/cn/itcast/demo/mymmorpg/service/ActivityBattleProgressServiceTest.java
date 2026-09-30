package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Activity;
import cn.itcast.demo.mymmorpg.repository.ActivityRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ActivityBattleProgressServiceTest {

    private ActivityRepository activityRepository;
    private ActivityService activityService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ActivityBattleProgressService service;

    @BeforeMethod
    public void setUp() {
        activityRepository = mock(ActivityRepository.class);
        activityService = mock(ActivityService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<MqInboxService> inbox = mock(ObjectProvider.class);
        when(inbox.getIfAvailable()).thenReturn(null);
        service = new ActivityBattleProgressService(
                activityRepository, activityService, inbox, redis, "activity-battle-consumer");
    }

    @Test
    public void win_projectsOpenedActivities() {
        Activity a = new Activity();
        a.setId(7L);
        a.setOpened(true);
        when(activityRepository.findByOpenedTrue()).thenReturn(List.of(a));

        List<Long> updated = service.applyBattleEnd(9L, 10001L, 1);
        assertThat(updated).containsExactly(7L);
        verify(activityService).addBattleWinProgress(9L, 7L, 1);
    }

    @Test
    public void lose_skips() {
        List<Long> updated = service.applyBattleEnd(9L, 10001L, 0);
        assertThat(updated).isEmpty();
        verifyNoInteractions(activityService);
    }

    @Test
    public void duplicate_idempotent() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);
        List<Long> updated = service.applyBattleEnd(9L, 10001L, 1);
        assertThat(updated).isEmpty();
        verifyNoInteractions(activityService);
    }

    @Test
    public void inboxClaimRejected_skipsDuplicateDelivery() {
        MqInboxService inbox = mock(MqInboxService.class);
        when(inbox.tryClaim(eq("activity-battle-consumer"), eq("battle:end:10001"),
                eq("BATTLE_EVENTS"), anyString())).thenReturn(false);
        @SuppressWarnings("unchecked")
        ObjectProvider<MqInboxService> inboxProvider = mock(ObjectProvider.class);
        when(inboxProvider.getIfAvailable()).thenReturn(inbox);
        service = new ActivityBattleProgressService(
                activityRepository, activityService, inboxProvider, redis, "activity-battle-consumer");

        assertThat(service.applyBattleEnd(9L, 10001L, 1)).isEmpty();
        verifyNoInteractions(activityService);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any());
    }

    @Test
    public void inboxClaimAccepted_projectsOnce() {
        MqInboxService inbox = mock(MqInboxService.class);
        when(inbox.tryClaim(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<MqInboxService> inboxProvider = mock(ObjectProvider.class);
        when(inboxProvider.getIfAvailable()).thenReturn(inbox);
        Activity a = new Activity();
        a.setId(7L);
        a.setOpened(true);
        when(activityRepository.findByOpenedTrue()).thenReturn(List.of(a));
        service = new ActivityBattleProgressService(
                activityRepository, activityService, inboxProvider, redis, "activity-battle-consumer");

        assertThat(service.applyBattleEnd(9L, 10001L, 1)).containsExactly(7L);
        verify(activityService).addBattleWinProgress(9L, 7L, 1);
    }
}
