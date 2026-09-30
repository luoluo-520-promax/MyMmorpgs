package cn.itcast.demo.mymmorpg.service;

import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CoopRoomServiceTest {

    @Test
    public void fourPlayerBossFightStaysInRoom() {
        CoopRoomService svc = new CoopRoomService(new NoOpSocialEventPublisher());
        CoopRoomService.CoopRoom room = svc.create(1L, "wb-1", 1000);
        svc.join(2L, room.roomId());
        svc.join(3L, room.roomId());
        CoopRoomService.CoopRoom full = svc.join(4L, room.roomId());
        assertThat(full.status()).isEqualTo(CoopRoomService.RoomStatus.FULL);
        assertThat(full.members()).hasSize(4);

        CoopRoomService.CoopRoom fighting = svc.startBoss(room.roomId(), 1L);
        assertThat(fighting.status()).isEqualTo(CoopRoomService.RoomStatus.IN_BOSS);

        Map<String, Object> after = svc.toView(svc.reportDamage(room.roomId(), 2L, 400));
        assertThat(after.get("bossHp")).isEqualTo(600L);
        assertThat(((Map<?, ?>) after.get("damageBoard")).get("2")).isEqualTo(400L);
    }

    @Test
    public void emoteSpectateLikeAndFlower() {
        CoopRoomService svc = new CoopRoomService(new NoOpSocialEventPublisher());
        CoopRoomService.CoopRoom room = svc.create(1L, "wb-2", 500);
        svc.join(2L, room.roomId());
        assertThat(svc.sendEmote(room.roomId(), 1L, "wave").get("ok")).isEqualTo(true);
        assertThat(svc.sendQuickPhrase(room.roomId(), 2L, "glhf").get("ok")).isEqualTo(true);
        Map<String, Object> spec = svc.spectate(99L, room.roomId());
        assertThat(spec.get("ok")).isEqualTo(true);
        assertThat(spec.get("spectatorCount")).isEqualTo(1);
        assertThat(svc.like(room.roomId(), 99L, 1L).get("ok")).isEqualTo(true);
        assertThat(svc.sendFlower(room.roomId(), 99L, 2L, 3).get("flowers")).isEqualTo(3L);
    }
}
