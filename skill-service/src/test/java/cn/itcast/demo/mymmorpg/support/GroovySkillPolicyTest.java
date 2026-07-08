/**
 * 文件说明：Groovy 技能数值策略单元测试。
 * 职责：验证 GroovySkillPolicy 与默认线性公式一致。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Groovy SkillPolicy 单元测试。
 */
public class GroovySkillPolicyTest {

    private static final Logger log = LoggerFactory.getLogger(GroovySkillPolicyTest.class);

    private SkillPolicy skillPolicy;

    @BeforeMethod
    public void setUp() {
        skillPolicy = new GroovySkillPolicy();
        log.info("[测试前置] GroovySkillPolicy 已加载");
    }

    @Test
    public void computeDamage_matchesDefaultFormula() {
        int playerLevel = 5;
        int skillId = 1001;
        int targetType = 1;
        log.info("[测试开始] 场景=Groovy伤害公式 | playerLevel={} | skillId={} | targetType={}",
                playerLevel, skillId, targetType);

        int damage = skillPolicy.computeDamage(playerLevel, skillId, targetType);

        log.info("[测试断言] 场景=Groovy伤害公式 | damage={} | 期望={}", damage, 141);
        assertThat(damage).isEqualTo(141);
    }

    @Test
    public void computeHeal_matchesDefaultFormula() {
        int playerLevel = 10;
        int skillId = 2001;
        log.info("[测试开始] 场景=Groovy治疗公式 | playerLevel={} | skillId={}", playerLevel, skillId);

        int heal = skillPolicy.computeHeal(playerLevel, skillId);

        log.info("[测试断言] 场景=Groovy治疗公式 | heal={} | 期望={}", heal, 140);
        assertThat(heal).isEqualTo(140);
    }
}
