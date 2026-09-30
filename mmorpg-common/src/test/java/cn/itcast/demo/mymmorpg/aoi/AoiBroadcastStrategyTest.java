package cn.itcast.demo.mymmorpg.aoi;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AoiBroadcastStrategyTest {

    @Test
    public void selectRecipientsByMode() {
        AoiBroadcastStrategy strategy = new AoiBroadcastStrategy();
        List<Long> viewers = List.of(1L, 2L, 3L, 4L);
        Set<Long> interest = Set.of(1L, 3L);
        Set<Long> dirty = Set.of(100L);

        assertThat(strategy.selectRecipients(AoiBroadcastStrategy.Mode.FULL, viewers, interest, dirty))
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(strategy.selectRecipients(AoiBroadcastStrategy.Mode.INTEREST_ONLY, viewers, interest, dirty))
                .containsExactly(1L, 3L);
        assertThat(strategy.selectRecipients(AoiBroadcastStrategy.Mode.DELTA_COMPRESSED, viewers, interest, dirty))
                .containsExactly(1L, 3L);
        assertThat(strategy.selectRecipients(AoiBroadcastStrategy.Mode.DELTA_COMPRESSED, viewers, interest, Set.of()))
                .isEmpty();
    }

    @Test
    public void shouldBroadcastSuppressesAndCounts() {
        AoiBroadcastStrategy strategy = new AoiBroadcastStrategy();
        strategy.configure(50f, 0.3f);

        assertThat(strategy.shouldBroadcast(AoiBroadcastStrategy.Mode.FULL, false, 10f, 0f)).isTrue();
        assertThat(strategy.shouldBroadcast(AoiBroadcastStrategy.Mode.INTEREST_ONLY, true, 10f, 0.1f)).isFalse();
        assertThat(strategy.shouldBroadcast(AoiBroadcastStrategy.Mode.DELTA_COMPRESSED, false, 10f, 1f)).isFalse();
        assertThat(strategy.shouldBroadcast(AoiBroadcastStrategy.Mode.DELTA_COMPRESSED, true, 10f, 0.9f)).isTrue();
        assertThat(strategy.shouldBroadcast(AoiBroadcastStrategy.Mode.FULL, true, 99f, 1f)).isFalse();

        assertThat(strategy.acceptedBroadcasts()).isEqualTo(2L);
        assertThat(strategy.suppressedBroadcasts()).isEqualTo(3L);
        assertThat(strategy.stats().get("suppressedBroadcasts")).isEqualTo(3L);
    }
}
