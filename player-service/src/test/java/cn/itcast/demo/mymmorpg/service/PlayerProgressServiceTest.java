/**
 * 文件说明：玩家进度服务单元测试。
 * 职责：验证等级曲线 calcLevel 与 addExp 经验累加、升级事件发布逻辑。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.event.PlayerLevelUpEvent;
import jforgame.commons.eventbus.EventBus;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlayerProgressService 单元测试。
 */
public class PlayerProgressServiceTest {

    private static final Logger log = LoggerFactory.getLogger(PlayerProgressServiceTest.class);

    @Mock
    private PlayerEntityCacheService playerEntityCacheService;
    @Mock
    private EventBus eventBus;

    private AutoCloseable mocks;
    private PlayerProgressService progressService;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        progressService = new PlayerProgressService(playerEntityCacheService, eventBus);
        log.info("[测试前置] PlayerProgressService 已初始化");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void calcLevel_levelOneAtZeroExp() {
        long exp = 0L;
        log.info("[测试开始] 场景=零经验等级 | exp={}", exp);

        int level = PlayerProgressService.calcLevel(exp);

        log.info("[测试断言] 场景=零经验等级 | level={} | 期望=1", level);
        assertThat(level).isEqualTo(1);
    }

    @Test
    public void calcLevel_levelTwoAtThousandExp() {
        long exp = 1000L;
        log.info("[测试开始] 场景=千经验二级 | exp={}", exp);

        int level = PlayerProgressService.calcLevel(exp);

        log.info("[测试断言] 场景=千经验二级 | level={} | 期望=2", level);
        assertThat(level).isEqualTo(2);
    }

    @Test
    public void calcLevel_levelFiveAtFourThousandExp() {
        long exp = 4000L;
        log.info("[测试开始] 场景=四千经验五级 | exp={}", exp);

        int level = PlayerProgressService.calcLevel(exp);

        log.info("[测试断言] 场景=四千经验五级 | level={} | 期望=5", level);
        assertThat(level).isEqualTo(5);
    }

    @Test
    public void calcLevel_negativeExpClampedToZero() {
        long exp = -500L;
        log.info("[测试开始] 场景=负经验钳制 | exp={}", exp);

        int level = PlayerProgressService.calcLevel(exp);

        log.info("[测试断言] 场景=负经验钳制 | level={} | 期望=1", level);
        assertThat(level).isEqualTo(1);
    }

    @Test
    public void addExp_accumulatesWithoutLevelUp() {
        long playerId = 1001L;
        int expGained = 300;
        Player player = player(1001L, 500L, 1);
        when(playerEntityCacheService.saveCacheAndMarkDirty(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));
        log.info("[测试开始] 场景=加经验不升级 | playerId={} | oldExp={} | oldLevel={} | expGained={}",
                playerId, player.getExp(), player.getLevel(), expGained);

        Player result = progressService.addExp(player, expGained);

        log.info("[测试断言] 场景=加经验不升级 | newExp={} | newLevel={} | 期望exp=800 | 期望level=1",
                result.getExp(), result.getLevel());
        assertThat(result.getExp()).isEqualTo(800L);
        assertThat(result.getLevel()).isEqualTo(1);
        verify(eventBus, never()).publish(any());
    }

    @Test
    public void addExp_triggersLevelUpEvent() {
        long playerId = 2002L;
        int expGained = 600;
        Player player = player(playerId, 900L, 1);
        when(playerEntityCacheService.saveCacheAndMarkDirty(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));
        log.info("[测试开始] 场景=加经验升级 | playerId={} | oldExp={} | oldLevel={} | expGained={}",
                playerId, player.getExp(), player.getLevel(), expGained);

        Player result = progressService.addExp(player, expGained);

        ArgumentCaptor<PlayerLevelUpEvent> captor = ArgumentCaptor.forClass(PlayerLevelUpEvent.class);
        verify(eventBus).publish(captor.capture());
        PlayerLevelUpEvent event = captor.getValue();
        log.info("[测试断言] 场景=加经验升级 | newExp={} | newLevel={} | oldLevel={} | eventNewLevel={} | 期望level=2",
                result.getExp(), result.getLevel(), event.getOldLevel(), event.getNewLevel());
        assertThat(result.getExp()).isEqualTo(1500L);
        assertThat(result.getLevel()).isEqualTo(2);
        assertThat(event.getOldLevel()).isEqualTo(1);
        assertThat(event.getNewLevel()).isEqualTo(2);
    }

    @Test
    public void addExp_ignoresInvalidInput() {
        int expGained = 100;
        log.info("[测试开始] 场景=无效输入忽略 | player=null | expGained={}", expGained);

        Player result = progressService.addExp(null, expGained);

        log.info("[测试断言] 场景=无效输入忽略 | result={} | 期望=null", result);
        assertThat(result).isNull();
        verify(playerEntityCacheService, never()).saveCacheAndMarkDirty(any());
        verify(eventBus, never()).publish(any());
    }

    @Test
    public void addExp_ignoresNonPositiveExp() {
        long playerId = 3003L;
        int expGained = 0;
        Player player = player(playerId, 100L, 1);
        log.info("[测试开始] 场景=零经验忽略 | playerId={} | expGained={}", playerId, expGained);

        Player result = progressService.addExp(player, expGained);

        log.info("[测试断言] 场景=零经验忽略 | resultExp={} | 期望exp=100", result.getExp());
        assertThat(result.getExp()).isEqualTo(100L);
        verify(playerEntityCacheService, never()).saveCacheAndMarkDirty(any());
        verify(eventBus, never()).publish(any());
    }

    private static Player player(long id, long exp, int level) {
        Player player = new Player();
        player.setId(id);
        player.setExp(exp);
        player.setLevel(level);
        return player;
    }
}
