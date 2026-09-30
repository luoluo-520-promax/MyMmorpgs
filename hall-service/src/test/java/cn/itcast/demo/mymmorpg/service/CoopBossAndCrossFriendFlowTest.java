package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 二游联机流程：跨服加好友 → 开 Coop 房 → 满 4 人 → 开 BOSS → 房间内分摊伤害击杀。
 * （替代全服抢伤广播）
 */
public class CoopBossAndCrossFriendFlowTest {

    @Test
    @SuppressWarnings("unchecked")
    public void crossFriendInviteThenFourPlayerRoomBoss() {
        ObjectProvider redis = mock(ObjectProvider.class);
        when(redis.getIfAvailable()).thenReturn(null);
        CrossServerFriendService friends = new CrossServerFriendService(redis);
        CoopRoomService coop = new CoopRoomService(new NoOpSocialEventPublisher());

        // 跨服好友双向关系
        assertThat(friends.addFriend(1L, 2L).get("ok")).isEqualTo(true);
        assertThat(friends.addFriend(1L, 3L).get("ok")).isEqualTo(true);
        assertThat(friends.addFriend(1L, 4L).get("ok")).isEqualTo(true);
        assertThat(friends.listFriends(1L)).containsExactlyInAnyOrder(2L, 3L, 4L);
        assertThat(friends.listFriends(2L)).containsExactly(1L);

        // 房主开房间（世界 BOSS 实例 HP 仅房间可见）
        CoopRoomService.CoopRoom room = coop.create(1L, "weekly-boss", 1000);
        coop.join(2L, room.roomId());
        coop.join(3L, room.roomId());
        CoopRoomService.CoopRoom full = coop.join(4L, room.roomId());
        assertThat(full.status()).isEqualTo(CoopRoomService.RoomStatus.FULL);
        assertThatThrownBy(() -> coop.join(5L, room.roomId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room_full");

        // 非房主不能开打
        assertThatThrownBy(() -> coop.startBoss(room.roomId(), 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not_leader");

        coop.startBoss(room.roomId(), 1L);
        assertThatThrownBy(() -> coop.reportDamage(room.roomId(), 99L, 100))
                .isInstanceOf(IllegalArgumentException.class);

        coop.reportDamage(room.roomId(), 1L, 250);
        coop.reportDamage(room.roomId(), 2L, 250);
        coop.reportDamage(room.roomId(), 3L, 250);
        CoopRoomService.CoopRoom killed = coop.reportDamage(room.roomId(), 4L, 250);
        assertThat(killed.bossHp()).isEqualTo(0L);
        assertThat(killed.status()).isEqualTo(CoopRoomService.RoomStatus.CLOSED);

        Map<String, Object> board = coop.damageBoard(room.roomId());
        assertThat(board).hasSize(4);
        assertThat(board.values()).allMatch(v -> ((Number) v).longValue() == 250L);

        Map<String, Object> view = coop.toView(killed);
        assertThat(view.get("ok")).isEqualTo(true);
        assertThat(view.get("maxMembers")).isEqualTo(4);

        // 好友解除不影响已结束房间历史伤害板
        friends.removeFriend(1L, 2L);
        assertThat(friends.listFriends(1L)).doesNotContain(2L);
        assertThat(coop.damageBoard(room.roomId())).hasSize(4);
    }
}
