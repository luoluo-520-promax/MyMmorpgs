package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.model.ai.PlayerAiProfile;
import cn.itcast.demo.mymmorpg.model.ai.PlayerBattleLite;
import cn.itcast.demo.mymmorpg.model.ai.SkillUsageAgg;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PlayerAiAdvisorServiceTest {

    @Test
    public void buildSuggestions_recommendsMissingHotSkill() {
        PlayerAiProfile profile = new PlayerAiProfile();
        profile.setName("测试员");
        profile.setLevel(10);
        profile.setPowerScore(200);
        profile.setLearnedSkillIds(List.of(1));
        profile.setTalentPoints(2);
        PlayerBattleLite personal = new PlayerBattleLite();
        personal.setEndedTotal(10);
        personal.setWinCount(3);
        personal.setLoseCount(7);
        personal.setWinRate(0.3);
        profile.setPersonalBattle(personal);

        BattleStatsSnapshot global = new BattleStatsSnapshot();
        global.setEndedTotal(100);
        global.setWinRate(0.55);
        global.setAvgDurationSec(90);
        SkillUsageAgg hot = new SkillUsageAgg();
        hot.setSkillId(1001);
        hot.setCastCount(50);
        hot.setUsageRate(0.4);
        global.setTopSkillUsage(List.of(hot));
        BattleStatsSnapshot.MonsterAgg monster = new BattleStatsSnapshot.MonsterAgg();
        monster.setMonsterTemplateId(200);
        monster.setTotal(40);
        monster.setWins(30);
        monster.setWinRate(0.75);
        global.getByMonsterTemplate().put(200, monster);

        List<String> suggestions = PlayerAiAdvisorService.buildSuggestions(
                profile, global, "怎么提高胜率？", "BATTLE");

        assertThat(suggestions).anyMatch(s -> s.contains("尚未学习") && s.contains("1001"));
        assertThat(suggestions).anyMatch(s -> s.contains("低于全服"));
        assertThat(suggestions).anyMatch(s -> s.contains("天赋点"));
    }
}
