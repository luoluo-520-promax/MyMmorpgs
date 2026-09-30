package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrossServerFriendServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    public void addAndListWithoutRedis() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        CrossServerFriendService svc = new CrossServerFriendService(provider);
        assertThat(svc.addFriend(10L, 20L).get("ok")).isEqualTo(true);
        assertThat(svc.listFriends(10L)).containsExactly(20L);
        assertThat(svc.listFriends(20L)).containsExactly(10L);
    }
}
