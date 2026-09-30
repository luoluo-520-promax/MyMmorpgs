package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 战令购买等级二次确认 + 月卡 Redis 天数上限。
 */
public class PassPurchaseAndMonthlyCardFlowTest {

    @Test
    @SuppressWarnings("unchecked")
    public void purchaseLevel_requiresConfirmToken() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PassService svc = new PassService(provider);

        Map<String, Object> req = svc.requestPurchaseLevel(7L, 1, 5);
        assertThat(req.get("ok")).isEqualTo(true);
        assertThat(req.get("requireConfirm")).isEqualTo(true);
        String token = (String) req.get("confirmToken");
        assertThat(token).startsWith("PL-7-1-5-");

        assertThat(svc.confirmPurchaseLevel(7L, 1, "bad-token").get("error"))
                .isEqualTo("confirm_token_invalid_or_used");

        Map<String, Object> confirmed = svc.confirmPurchaseLevel(7L, 1, token);
        assertThat(confirmed.get("ok")).isEqualTo(true);
        assertThat(confirmed.get("purchased")).isEqualTo(true);
        assertThat(confirmed.get("level")).isEqualTo(5);

        // token 一次性
        assertThat(svc.confirmPurchaseLevel(7L, 1, token).get("error"))
                .isEqualTo("confirm_token_invalid_or_used");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void monthlyCard_rejectsWhenRemainingOver180() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(MonthlyCardRedisStore.key(9L))).thenReturn("181");

        ObjectProvider<PassService> passProvider = mock(ObjectProvider.class);
        when(passProvider.getIfAvailable()).thenReturn(null);
        MonthlyCardRedisStore store = new MonthlyCardRedisStore(redis, passProvider);
        assertThat(store.activateOrRenew(9L, 30)).isFalse();
        assertThat(store.remainingDays(9L)).isEqualTo(181);

        when(ops.get(MonthlyCardRedisStore.key(8L))).thenReturn("10");
        assertThat(store.activateOrRenew(8L, 30)).isTrue();
        verify(ops).set(eq(MonthlyCardRedisStore.key(8L)), eq("40"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void activateMonthlyCard_usesRedisStoreCap() {
        MonthlyCardRedisStore store = mock(MonthlyCardRedisStore.class);
        when(store.activateOrRenew(11L, 30)).thenReturn(false);
        when(store.remainingDays(11L)).thenReturn(200);

        ObjectProvider tlog = mock(ObjectProvider.class);
        when(tlog.getIfAvailable()).thenReturn(null);
        ObjectProvider<MonthlyCardRedisStore> storeProvider = mock(ObjectProvider.class);
        when(storeProvider.getIfAvailable()).thenReturn(store);

        PassService svc = new PassService(tlog, storeProvider);
        Map<String, Object> out = svc.activateMonthlyCard(11L, 1, 30);
        assertThat(out.get("ok")).isEqualTo(false);
        assertThat(out.get("error")).isEqualTo("monthly_card_cap_exceeded");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void onQuestCompleted_addsXp() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        PassService svc = new PassService(provider);
        Map<String, Object> out = svc.onQuestCompleted(3L, 1, 1001, 1200);
        assertThat(out.get("level")).isEqualTo(2);
        assertThat(out.get("xp")).isEqualTo(200);
    }
}
