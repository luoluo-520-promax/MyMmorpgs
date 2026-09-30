package cn.itcast.demo.mymmorpg.world.loot;

import org.testng.annotations.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class LootOwnershipPolicyRandomShareTest {

    @Test
    public void randomShare_assignsSingleWinnerFromParty() {
        LootOwnershipPolicy.LootDecision d = LootOwnershipPolicy.decide(
                LootOwnershipPolicy.SyncMode.RANDOM_SHARE,
                1L, 1L, Set.of(1L, 2L, 3L), 1L);
        assertThat(d.allowed()).isTrue();
        assertThat(d.pickupEligibleIds()).hasSize(1);
        assertThat(d.ownerPlayerId()).isIn(1L, 2L, 3L);
        assertThat(d.reason()).isEqualTo("random_share_assigned");
    }

    @Test
    public void randomShare_rejectsNonMember() {
        LootOwnershipPolicy.LootDecision d = LootOwnershipPolicy.decide(
                LootOwnershipPolicy.SyncMode.RANDOM_SHARE,
                9L, 1L, Set.of(1L, 2L), 0L);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("not_party_member");
    }
}
