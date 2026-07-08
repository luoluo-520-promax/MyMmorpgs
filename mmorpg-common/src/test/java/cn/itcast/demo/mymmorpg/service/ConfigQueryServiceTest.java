/**
 * 文件说明：策划表查询服务单元测试。
 * 职责：验证 ConfigQueryService 委托仓储查询并返回正确结果。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.MapConfig;
import cn.itcast.demo.mymmorpg.repository.BuffConfigRepository;
import cn.itcast.demo.mymmorpg.repository.ItemConfigRepository;
import cn.itcast.demo.mymmorpg.repository.MapConfigRepository;
import cn.itcast.demo.mymmorpg.repository.MonsterConfigRepository;
import cn.itcast.demo.mymmorpg.repository.SkillConfigRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigQueryServiceTest {

    private static final Logger log = LoggerFactory.getLogger(ConfigQueryServiceTest.class);

    private MapConfigRepository mapConfigRepository;
    private MonsterConfigRepository monsterConfigRepository;
    private SkillConfigRepository skillConfigRepository;
    private BuffConfigRepository buffConfigRepository;
    private ItemConfigRepository itemConfigRepository;
    private ConfigQueryService configQueryService;

    @BeforeMethod
    public void setUp() {
        mapConfigRepository = mock(MapConfigRepository.class);
        monsterConfigRepository = mock(MonsterConfigRepository.class);
        skillConfigRepository = mock(SkillConfigRepository.class);
        buffConfigRepository = mock(BuffConfigRepository.class);
        itemConfigRepository = mock(ItemConfigRepository.class);
        configQueryService = new ConfigQueryService(
                mapConfigRepository,
                monsterConfigRepository,
                skillConfigRepository,
                buffConfigRepository,
                itemConfigRepository
        );
        log.info("[测试前置] ConfigQueryService 已加载");
    }

    @Test
    public void findMapById_returnsConfigWhenExists() {
        int mapId = 1001;
        MapConfig mapConfig = new MapConfig();
        mapConfig.setId(mapId);
        mapConfig.setName("新手村");
        mapConfig.setWidth(256);
        mapConfig.setHeight(256);
        when(mapConfigRepository.findById(mapId)).thenReturn(Optional.of(mapConfig));
        log.info("[测试开始] 场景=按ID查地图 | mapId={} | mapName={}", mapId, mapConfig.getName());

        MapConfig found = configQueryService.findMapById(mapId);

        log.info("[测试断言] 场景=按ID查地图 | foundId={} | foundName={} | 期望id={}",
                found.getId(), found.getName(), mapId);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(mapId);
        assertThat(found.getName()).isEqualTo("新手村");
        verify(mapConfigRepository).findById(mapId);
    }

    @Test
    public void findMapById_returnsNullWhenMissing() {
        int mapId = 9999;
        when(mapConfigRepository.findById(mapId)).thenReturn(Optional.empty());
        log.info("[测试开始] 场景=地图不存在 | mapId={}", mapId);

        MapConfig found = configQueryService.findMapById(mapId);

        log.info("[测试断言] 场景=地图不存在 | found={} | 期望=null", found);
        assertThat(found).isNull();
    }
}
