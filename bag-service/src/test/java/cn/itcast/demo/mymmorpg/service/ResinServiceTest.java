package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResinServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void consumeAndBuyRespectLimits() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        ResinService svc = new ResinService(provider);
        Map<String, Object> st = svc.status(1L);
        assertThat(st.get("current")).isEqualTo(ResinService.MAX_RESIN);
        assertThat(svc.consume(1L, 40).get("current")).isEqualTo(ResinService.MAX_RESIN - 40);
        assertThat(svc.buy(1L).get("ok")).isEqualTo(true);
        assertThat(svc.buy(1L).get("buyCountToday")).isEqualTo(2);
    }
}
