/**
 * 文件说明：BattleSceneFactory 单元测试类。
 * 职责：验证 createState 方法是否正确映射各入参到 BattleRuntimeState 对应字段，并输出中文测试日志。
 */
package cn.itcast.demo.mymmorpg.model;

import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.service.BattleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BattleSceneFactory 单元测试：验证字段映射与默认值逻辑。
 */
public class BattleSceneFactoryTest {

    private static final Logger log = LoggerFactory.getLogger(BattleSceneFactoryTest.class);

    private BattleSceneFactory factory;

    @BeforeMethod
    public void setUp() {
        factory = new BattleSceneFactory();
        log.info("[测试前置] BattleSceneFactory 已初始化");
    }

    @Test
    public void createState_mapsFields() {
        long battleId = 1001L;
        int sceneId = 3;
        long playerId = 42L;
        long lineupId = 7L;
        long enemyEntityId = 200L;
        int expReward = 150;
        int battleType = 1;

        MonsterConfig mc = new MonsterConfig();
        mc.setId(99);
        Player p = new Player();
        p.setName("hero");
        p.setLevel(5);

        log.info("[测试开始] 场景=字段映射 | battleId={} | sceneId={} | playerId={} | lineupId={} | enemyEntityId={} | monsterTemplateId={}",
                battleId, sceneId, playerId, lineupId, enemyEntityId, mc.getId());

        BattleService.BattleRuntimeState s = factory.createState(
                battleId, sceneId, playerId, lineupId, enemyEntityId, mc, p, "goblin", 2, expReward, battleType);

        log.info("战局初始化: battleId={}, sceneId={}, enemyEntityId={}, battleType={}",
                s.battleId, s.sceneId, s.enemyEntityId, s.battleType);
        log.info("[测试断言] 场景=字段映射 | playerName={} | enemyName={} | playerLevel={} | enemyLevel={} | expReward={}",
                s.playerName, s.enemyName, s.playerLevel, s.enemyLevel, s.expReward);

        assertThat(s.battleId).isEqualTo(battleId);
        assertThat(s.playerId).isEqualTo(playerId);
        assertThat(s.sceneId).isEqualTo(sceneId);
        assertThat(s.lineupId).isEqualTo(lineupId);
        assertThat(s.enemyEntityId).isEqualTo(enemyEntityId);
        assertThat(s.monsterTemplateId).isEqualTo(99);
        assertThat(s.expReward).isEqualTo(expReward);
        assertThat(s.playerName).isEqualTo("hero");
        assertThat(s.enemyName).isEqualTo("goblin");
        assertThat(s.playerLevel).isEqualTo(5);
        assertThat(s.enemyLevel).isEqualTo(2);
        assertThat(s.battleType).isEqualTo(battleType);
    }

    @Test
    public void createState_nullPlayerLevel_defaultsToOne() {
        MonsterConfig mc = new MonsterConfig();
        mc.setId(1);
        Player p = new Player();
        p.setName("n");
        p.setLevel(null);

        log.info("[测试开始] 场景=玩家等级为空 | playerName={} | playerLevel=null | 期望默认等级=1", p.getName());

        BattleService.BattleRuntimeState s = factory.createState(
                1L, 1, 1L, 1L, 1L, mc, p, "e", 1, 0, 0);

        log.info("[测试断言] 场景=玩家等级为空 | playerLevel={}", s.playerLevel);
        assertThat(s.playerLevel).isEqualTo(1);
    }
}
