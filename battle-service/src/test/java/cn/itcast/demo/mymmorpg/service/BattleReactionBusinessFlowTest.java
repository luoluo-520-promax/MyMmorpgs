package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.reaction.BattleReactionService;
import cn.itcast.demo.mymmorpg.web.InternalBattleReactionController;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * battle-service P7 瞬时博弈 + 重播业务流程。
 */
public class BattleReactionBusinessFlowTest {

    private InternalBattleReactionController api;
    private BattleReactionService reactions;
    private BattleReplayService replays;

    @BeforeMethod
    public void setUp() {
        reactions = new BattleReactionService();
        replays = new BattleReplayService();
        api = new InternalBattleReactionController(reactions, replays);
    }

    @Test
    public void perfectDodgeThenParryThenReplayPlayback() {
        Map<String, Object> open = api.openWindow(Map.of(
                "attackId", "bat-atk-1",
                "attackerEntityId", 501L));
        assertThat(open.get("ok")).isEqualTo(true);
        assertThat(open.get("dodgeWindowMs")).isEqualTo(200);

        long now = System.currentTimeMillis();
        Map<String, Object> dodge = api.perfectDodge(Map.of(
                "battleId", "bat-1",
                "playerId", 42L,
                "attackId", "bat-atk-1",
                "clientTs", now));
        assertThat(dodge.get("ok")).isEqualTo(true);
        assertThat(dodge.get("event")).isEqualTo("bulletTimeStart");
        assertThat(reactions.validator().perfectDodgeCount("bat-1")).isEqualTo(1);

        api.openWindow(Map.of("attackId", "bat-atk-2", "attackerEntityId", 501L));
        Map<String, Object> parry = api.parry(Map.of(
                "battleId", "bat-1",
                "playerId", 42L,
                "attackId", "bat-atk-2",
                "clientTs", System.currentTimeMillis()));
        assertThat(parry.get("ok")).isEqualTo(true);
        assertThat(parry.get("stunAttackerMs")).isEqualTo(800);

        Map<String, Object> start = api.replayStart(Map.of("battleId", "bat-replay-1", "seed", 7L));
        String replayId = String.valueOf(start.get("replayId"));
        replays.append(replayId, 1, 1L, "PERFECT_DODGE", Map.of("damage", 0));
        replays.append(replayId, 2, 2L, "SKILL", Map.of("damage", 80));
        Map<String, Object> play = api.replayPlayback(Map.of(
                "replayId", replayId, "speedMul", 2d));
        assertThat(play.get("ok")).isEqualTo(true);
        assertThat(((Number) play.get("frameCount")).intValue()).isEqualTo(2);
        assertThat(((Number) play.get("speedMul")).doubleValue()).isEqualTo(2.0);
    }

    @Test
    public void missingWindowRejectsPerfectDodge() {
        Map<String, Object> miss = api.perfectDodge(Map.of(
                "battleId", "bat-x",
                "playerId", 1L,
                "attackId", "never-opened",
                "clientTs", System.currentTimeMillis()));
        assertThat(miss.get("ok")).isEqualTo(false);
        assertThat(miss.get("error")).isEqualTo("window_not_found");
    }
}
