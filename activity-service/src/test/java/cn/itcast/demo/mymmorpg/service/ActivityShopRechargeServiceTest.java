package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Activity;
import cn.itcast.demo.mymmorpg.model.ActivityTypes;
import cn.itcast.demo.mymmorpg.repository.ActivityRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 二游累充：商城实付驱动首充活动进度（HTTP/MQ 共用幂等）。
 */
public class ActivityShopRechargeServiceTest {

    private ActivityRepository activityRepository;
    private ActivityService activityService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ActivityShopRechargeService service;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        activityRepository = mock(ActivityRepository.class);
        activityService = mock(ActivityService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        ObjectProvider<MqInboxService> inbox = mock(ObjectProvider.class);
        when(inbox.getIfAvailable()).thenReturn(null);
        service = new ActivityShopRechargeService(
                activityRepository, activityService, redis, inbox, "activity-shop-consumer");
    }

    @Test
    public void applyRecharge_firstRechargeOpened_updatesProgress() {
        Activity a = opened(11L, ActivityTypes.FIRST_RECHARGE.getCode());
        when(activityRepository.findByOpenedTrue()).thenReturn(List.of(a));

        List<Long> updated = service.applyRecharge(9L, 100L, "O1");

        assertThat(updated).containsExactly(11L);
        verify(activityService).addRechargeProgress(9L, 11L, 100L);
        verify(valueOps).setIfAbsent(eq("activity:recharge:order:O1"), eq("1"), any());
    }

    @Test
    public void applyRecharge_duplicateOrder_skips() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        List<Long> updated = service.applyRecharge(9L, 100L, "O1");

        assertThat(updated).isEmpty();
        verifyNoInteractions(activityService);
        verify(activityRepository, never()).findByOpenedTrue();
    }

    @Test
    public void applyRecharge_noFirstRechargeActivity_stillClaimsIdempotency() {
        Activity other = opened(12L, ActivityTypes.SUMMER_SIGN_IN.getCode());
        when(activityRepository.findByOpenedTrue()).thenReturn(List.of(other));

        List<Long> updated = service.applyRecharge(9L, 50L, "O2");

        assertThat(updated).isEmpty();
        verify(activityService, never()).addRechargeProgress(anyLong(), anyLong(), anyLong());
        verify(valueOps).setIfAbsent(eq("activity:recharge:order:O2"), eq("1"), any());
    }

    @Test
    public void applyRecharge_invalidInput_empty() {
        assertThat(service.applyRecharge(0L, 100L, "O1")).isEmpty();
        assertThat(service.applyRecharge(9L, 0L, "O1")).isEmpty();
        verifyNoInteractions(activityService);
    }

    @Test
    public void applyRecharge_inboxClaimFailed_skips() {
        @SuppressWarnings("unchecked")
        ObjectProvider<MqInboxService> inboxProvider = mock(ObjectProvider.class);
        MqInboxService inbox = mock(MqInboxService.class);
        when(inboxProvider.getIfAvailable()).thenReturn(inbox);
        when(inbox.tryClaim(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        service = new ActivityShopRechargeService(
                activityRepository, activityService, redis, inboxProvider, "activity-shop-consumer");

        List<Long> updated = service.applyRecharge(9L, 100L, "O3");

        assertThat(updated).isEmpty();
        verifyNoInteractions(activityService);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any());
    }

    private static Activity opened(long id, int type) {
        Activity a = new Activity();
        a.setId(id);
        a.setOpened(true);
        a.setType(type);
        return a;
    }
}
