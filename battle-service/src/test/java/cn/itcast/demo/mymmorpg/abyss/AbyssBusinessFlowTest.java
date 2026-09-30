package cn.itcast.demo.mymmorpg.abyss;

import cn.itcast.demo.mymmorpg.client.HallMailClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 深渊完整业务流程：开局祝福 → 12 间连打保血量 → 9/12/15 星邮件 → 失败不推进 → 重置。
 */
public class AbyssBusinessFlowTest {

    private AbyssService abyssService;
    private AbyssFloorConfigLoader configLoader;
    private HallMailClient hallMailClient;
    private final List<Map<String, Object>> mails = new ArrayList<>();

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        mails.clear();
        ObjectMapper mapper = new ObjectMapper();
        configLoader = new AbyssFloorConfigLoader(
                mapper, new DefaultResourceLoader(), "classpath:config/abyss/abyss_floor_config.json");
        hallMailClient = mock(HallMailClient.class);
        when(hallMailClient.sendMail(any())).thenAnswer(inv -> {
            Map<String, Object> body = inv.getArgument(0);
            mails.add(Map.copyOf(body));
            return Map.of("ok", true, "mailId", mails.size());
        });
        ObjectProvider redisProvider = mock(ObjectProvider.class);
        ObjectProvider<HallMailClient> mailProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        when(mailProvider.getIfAvailable()).thenReturn(hallMailClient);
        abyssService = new AbyssService(configLoader, redisProvider, mailProvider, mapper);
    }

    @Test
    public void twelveChambersFullClear_reaches15StarsAndSendsThreeMails() {
        long playerId = 777L;
        Map<String, Object> start = abyssService.start(playerId);
        assertThat(start.get("ok")).isEqualTo(true);
        assertThat(start.get("monsterGroup")).isEqualTo("abyss_f1_c1");
        assertThat(((Map<?, ?>) start.get("blessing")).get("bonus")).isEqualTo(0.3);

        int hp = 10000;
        int energy = 0;
        for (int i = 0; i < 12; i++) {
            hp -= 100;
            energy += 5;
            // 全部满星：剩余 60s+
            Map<String, Object> finish = abyssService.finishChamber(
                    playerId, true, hp, energy, List.of(100 * i), 90);
            assertThat(finish.get("ok")).isEqualTo(true);
            assertThat(finish.get("starsGained")).isEqualTo(3);
            assertThat(finish.get("hp")).isEqualTo(hp);
            assertThat(finish.get("failed")).isNull();
        }

        Map<String, Object> progress = abyssService.progress(playerId);
        assertThat(progress.get("totalStars")).isEqualTo(36);
        assertThat(progress.get("active")).isEqualTo(true);

        assertThat(mails).hasSize(3);
        assertThat(mails.get(0).get("attachmentsJson")).isEqualTo(AbyssService.attachmentsFor(9));
        assertThat(mails.get(1).get("attachmentsJson")).isEqualTo(AbyssService.attachmentsFor(12));
        assertThat(mails.get(2).get("attachmentsJson")).isEqualTo(AbyssService.attachmentsFor(15));
        assertThat(String.valueOf(mails.get(2).get("attachmentsJson"))).contains("30001");

        ArgumentCaptor<Map<String, Object>> mailCap = ArgumentCaptor.forClass(Map.class);
        verify(hallMailClient, times(3)).sendMail(mailCap.capture());
        assertThat(mailCap.getAllValues()).allMatch(m ->
                ((Number) m.get("playerId")).longValue() == playerId);
    }

    @Test
    public void failChamber_keepsPositionAndZeroStars() {
        long playerId = 88L;
        abyssService.start(playerId);
        Map<String, Object> fail = abyssService.finishChamber(
                playerId, false, 500, 10, List.of(1), 0);
        assertThat(fail.get("failed")).isEqualTo(true);
        assertThat(fail.get("starsGained")).isEqualTo(0);
        assertThat(fail.get("chamberIndex")).isEqualTo(1);
        assertThat(fail.get("hp")).isEqualTo(500);

        Map<String, Object> retry = abyssService.finishChamber(
                playerId, true, 500, 10, List.of(1), 45);
        assertThat(retry.get("starsGained")).isEqualTo(2);
        assertThat(retry.get("chamberIndex")).isEqualTo(2);
    }

    @Test
    public void milestoneTriggeredExactlyOnce_atBoundaries() {
        long playerId = 55L;
        abyssService.start(playerId);
        // 3 间满星 = 9 星 → 触发 9 星邮件
        for (int i = 0; i < 3; i++) {
            abyssService.finishChamber(playerId, true, 9000 - i, 1, List.of(), 70);
        }
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).get("title").toString()).contains("9");

        // 再 1 间满星 = 12 星
        abyssService.finishChamber(playerId, true, 8000, 1, List.of(), 70);
        assertThat(mails).hasSize(2);

        // 再 1 间满星 = 15 星
        abyssService.finishChamber(playerId, true, 7900, 1, List.of(), 70);
        assertThat(mails).hasSize(3);

        // 继续通关不再重复发信
        abyssService.finishChamber(playerId, true, 7800, 1, List.of(), 70);
        assertThat(mails).hasSize(3);
    }

    @Test
    public void resetPlayer_clearsSessionAndStars() {
        long playerId = 9L;
        abyssService.start(playerId);
        abyssService.finishChamber(playerId, true, 9000, 0, List.of(), 60);
        assertThat(abyssService.progress(playerId).get("totalStars")).isEqualTo(3);

        abyssService.resetPlayer(playerId);
        Map<String, Object> after = abyssService.progress(playerId);
        assertThat(after.get("active")).isEqualTo(false);
        assertThat(after.get("totalStars")).isEqualTo(0);
    }

    @Test
    public void configReload_keepsSeasonReadable() {
        configLoader.reload();
        assertThat(configLoader.current().getSeasonId()).isEqualTo("2026-08-a");
        assertThat(configLoader.current().getFloors()).hasSize(3);
        assertThat(configLoader.current().getFloors().get(0).getChambers()).hasSize(4);
    }
}
