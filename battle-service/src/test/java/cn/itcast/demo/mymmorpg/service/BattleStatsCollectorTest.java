package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.model.ai.PlayerBattleLite;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BattleStatsCollectorTest {

    @Test
    public void recordEnded_aggregatesWinRateByMonsterAndPlayer() {
        BattleStatsCollector collector = new BattleStatsCollector();
        collector.recordEnded(10L, 1, 1, 30, 200, 10, 20);
        collector.recordEnded(10L, 2, 1, 40, 200, 10, 20);
        collector.recordEnded(10L, 3, 0, 50, 200, 10, 0);
        collector.recordEnded(11L, 4, 2, 20, 201, 12, 0);
        collector.recordSkillCast(10L, 1001);
        collector.recordSkillCast(10L, 1001);
        collector.recordSkillCast(11L, 1002);
        collector.observeActiveBindings(3);

        BattleStatsSnapshot snap = collector.snapshot(10500, 2);
        assertThat(snap.getEndedTotal()).isEqualTo(4);
        assertThat(snap.getWinCount()).isEqualTo(2);
        assertThat(snap.getLoseCount()).isEqualTo(1);
        assertThat(snap.getDrawCount()).isEqualTo(1);
        assertThat(snap.getWinRate()).isEqualTo(0.5);
        assertThat(snap.getByMonsterTemplate().get(200).getTotal()).isEqualTo(3);
        assertThat(snap.getByMonsterTemplate().get(200).getWins()).isEqualTo(2);
        assertThat(snap.getMaxActiveBindingsObserved()).isGreaterThanOrEqualTo(3);
        assertThat(snap.getAvgDurationSec()).isEqualTo(35.0);
        assertThat(snap.getSkillCastTotal()).isEqualTo(3);
        assertThat(snap.getTopSkillUsage()).isNotEmpty();
        assertThat(snap.getTopSkillUsage().get(0).getSkillId()).isEqualTo(1001);

        PlayerBattleLite personal = collector.playerStats(10L);
        assertThat(personal.getEndedTotal()).isEqualTo(3);
        assertThat(personal.getWinCount()).isEqualTo(2);
        assertThat(personal.getWinRate()).isEqualTo(2.0 / 3.0);
        assertThat(personal.getSkillCastTotal()).isEqualTo(2);
    }
}
