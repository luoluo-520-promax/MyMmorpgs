package cn.itcast.demo.mymmorpg.client;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 熔断降级：scene/hall/battle FallbackFactory 在故障时返回空负载或可调用桩。
 */
public class CommandClientFallbackFactoryTest {

    @Test
    public void sceneFallback_returnsEmptyPayloads() {
        SceneCommandClient client = new SceneCommandClientFallbackFactory()
                .create(new RuntimeException("scene down"));
        assertThat(client.enter(1L, new byte[]{1})).isEmpty();
        assertThat(client.move(1L, new byte[]{1})).isEmpty();
        assertThat(client.transfer(1L, new byte[]{1})).isEmpty();
        assertThat(client.nearby(1L, new byte[]{1})).isEmpty();
        client.leave(1L);
        client.disconnect(1L);
    }

    @Test
    public void hallFallback_returnsEmptyPayloads() {
        HallCommandClient client = new HallCommandClientFallbackFactory()
                .create(new RuntimeException("hall down"));
        assertThat(client.friends(1L, new byte[]{1})).isEmpty();
        assertThat(client.addFriend(1L, new byte[]{1})).isEmpty();
        assertThat(client.mails(1L, new byte[]{1})).isEmpty();
        assertThat(client.claimMail(1L, new byte[]{1})).isEmpty();
        assertThat(client.ranking(1L, new byte[]{1})).isEmpty();
    }

    @Test
    public void battleFallback_returnsEmptyPayloads() {
        BattleCommandClient client = new BattleCommandClientFallbackFactory()
                .create(new RuntimeException("battle down"));
        assertThat(client.start(1L, new byte[]{1})).isEmpty();
        assertThat(client.action(1L, new byte[]{1})).isEmpty();
        assertThat(client.end(1L, new byte[]{1})).isEmpty();
    }
}
