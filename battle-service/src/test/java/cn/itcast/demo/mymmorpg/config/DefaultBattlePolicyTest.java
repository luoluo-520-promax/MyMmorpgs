/**
 * 文件说明：默认战斗数值策略单元测试。
 * 职责：验证 BattlePolicyConfiguration 中默认伤害与治疗计算公式。
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.support.BattlePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认 BattlePolicy 单元测试。
 */
public class DefaultBattlePolicyTest {

    private static final Logger log = LoggerFactory.getLogger(DefaultBattlePolicyTest.class);

    private BattlePolicy battlePolicy;

    @BeforeMethod
    public void setUp() {
        battlePolicy = new BattlePolicyConfiguration().battlePolicy();
        log.info("[测试前置] 默认 BattlePolicy 已加载");
    }

    @Test
    public void computeDamage_normalAttack() {
        int attackerAttack = 65;
        int targetDefense = 4;
        int actionType = 1;
        log.info("[测试开始] 场景=普攻伤害 | attackerAttack={} | targetDefense={} | actionType={}",
                attackerAttack, targetDefense, actionType);

        int damage = battlePolicy.computeDamage(attackerAttack, targetDefense, actionType, 0);

        log.info("[测试断言] 场景=普攻伤害 | damage={} | 期望={}", damage, 61);
        assertThat(damage).isEqualTo(61);
    }

    @Test
    public void computeDamage_skillWithBonus() {
        int attackerAttack = 65;
        int targetDefense = 4;
        int actionType = 2;
        int skillId = 1001;
        log.info("[测试开始] 场景=技能伤害 | attackerAttack={} | targetDefense={} | actionType={} | skillId={}",
                attackerAttack, targetDefense, actionType, skillId);

        int damage = battlePolicy.computeDamage(attackerAttack, targetDefense, actionType, skillId);

        log.info("[测试断言] 场景=技能伤害 | damage={} | 期望={}", damage, 91);
        assertThat(damage).isEqualTo(91);
    }

    @Test
    public void computeDamage_minimumOne() {
        int attackerAttack = 3;
        int targetDefense = 10;
        log.info("[测试开始] 场景=最低伤害 | attackerAttack={} | targetDefense={}", attackerAttack, targetDefense);

        int damage = battlePolicy.computeDamage(attackerAttack, targetDefense, 1, 0);

        log.info("[测试断言] 场景=最低伤害 | damage={} | 期望=1", damage);
        assertThat(damage).isEqualTo(1);
    }

    @Test
    public void computeHeal_withItem() {
        log.info("[测试开始] 场景=道具治疗 | actionType=3 | itemId=1001");

        int heal = battlePolicy.computeHeal(3, 1001);

        log.info("[测试断言] 场景=道具治疗 | heal={} | 期望=150", heal);
        assertThat(heal).isEqualTo(150);
    }

    @Test
    public void computeHeal_withoutItemId() {
        log.info("[测试开始] 场景=默认道具治疗 | actionType=3 | itemId=0");

        int heal = battlePolicy.computeHeal(3, 0);

        log.info("[测试断言] 场景=默认道具治疗 | heal={} | 期望=100", heal);
        assertThat(heal).isEqualTo(100);
    }

    @Test
    public void computeHeal_nonItemAction() {
        log.info("[测试开始] 场景=非道具行动不治疗 | actionType=1");

        int heal = battlePolicy.computeHeal(1, 1001);

        log.info("[测试断言] 场景=非道具行动不治疗 | heal={} | 期望=0", heal);
        assertThat(heal).isZero();
    }
}
