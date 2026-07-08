/**
 * 文件说明：默认技能数值策略单元测试。
 * 职责：验证 SkillPolicyConfiguration 中默认伤害与治疗计算公式。
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.support.SkillPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认 SkillPolicy 单元测试。
 */
public class DefaultSkillPolicyTest {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillPolicyTest.class);

    private SkillPolicy skillPolicy;

    @BeforeMethod
    public void setUp() {
        skillPolicy = new SkillPolicyConfiguration().skillPolicy();
        log.info("[测试前置] 默认 SkillPolicy 已加载");
    }

    @Test
    public void computeDamage_normalLevel() {
        int playerLevel = 5;
        int skillId = 1001;
        int targetType = 1;
        log.info("[测试开始] 场景=技能伤害 | playerLevel={} | skillId={} | targetType={}",
                playerLevel, skillId, targetType);

        int damage = skillPolicy.computeDamage(playerLevel, skillId, targetType);

        log.info("[测试断言] 场景=技能伤害 | damage={} | 期望={}", damage, 141);
        assertThat(damage).isEqualTo(141);
    }

    @Test
    public void computeDamage_highLevelWithSkillBonus() {
        int playerLevel = 10;
        int skillId = 1030;
        int targetType = 1;
        log.info("[测试开始] 场景=高等级技能伤害 | playerLevel={} | skillId={} | targetType={}",
                playerLevel, skillId, targetType);

        int damage = skillPolicy.computeDamage(playerLevel, skillId, targetType);

        log.info("[测试断言] 场景=高等级技能伤害 | damage={} | 期望={}", damage, 230);
        assertThat(damage).isEqualTo(230);
    }

    @Test
    public void computeDamage_minimumLevel() {
        int playerLevel = 0;
        int skillId = 100;
        int targetType = 1;
        log.info("[测试开始] 场景=最低等级伤害 | playerLevel={} | skillId={} | targetType={}",
                playerLevel, skillId, targetType);

        int damage = skillPolicy.computeDamage(playerLevel, skillId, targetType);

        log.info("[测试断言] 场景=最低等级伤害 | damage={} | 期望={}", damage, 92);
        assertThat(damage).isEqualTo(92);
    }

    @Test
    public void computeDamage_negativeLevelUsesFloorOne() {
        int playerLevel = -1;
        int skillId = 1049;
        int targetType = 2;
        log.info("[测试开始] 场景=负等级按1级计算 | playerLevel={} | skillId={} | targetType={}",
                playerLevel, skillId, targetType);

        int damage = skillPolicy.computeDamage(playerLevel, skillId, targetType);

        log.info("[测试断言] 场景=负等级按1级计算 | damage={} | 期望={}", damage, 141);
        assertThat(damage).isEqualTo(141);
    }

    @Test
    public void computeHeal_normalLevel() {
        int playerLevel = 5;
        int skillId = 2001;
        log.info("[测试开始] 场景=技能治疗 | playerLevel={} | skillId={}", playerLevel, skillId);

        int heal = skillPolicy.computeHeal(playerLevel, skillId);

        log.info("[测试断言] 场景=技能治疗 | heal={} | 期望={}", heal, 100);
        assertThat(heal).isEqualTo(100);
    }

    @Test
    public void computeHeal_highLevel() {
        int playerLevel = 10;
        int skillId = 2001;
        log.info("[测试开始] 场景=高等级治疗 | playerLevel={} | skillId={}", playerLevel, skillId);

        int heal = skillPolicy.computeHeal(playerLevel, skillId);

        log.info("[测试断言] 场景=高等级治疗 | heal={} | 期望={}", heal, 140);
        assertThat(heal).isEqualTo(140);
    }

    @Test
    public void computeHeal_minimumLevel() {
        int playerLevel = 0;
        int skillId = 2001;
        log.info("[测试开始] 场景=最低等级治疗 | playerLevel={} | skillId={}", playerLevel, skillId);

        int heal = skillPolicy.computeHeal(playerLevel, skillId);

        log.info("[测试断言] 场景=最低等级治疗 | heal={} | 期望={}", heal, 68);
        assertThat(heal).isEqualTo(68);
    }
}
