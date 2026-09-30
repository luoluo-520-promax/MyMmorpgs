package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.center.MigrationTicketService;
import cn.itcast.demo.mymmorpg.center.SceneMigrationPlan;
import cn.itcast.demo.mymmorpg.config.SceneRuntimeProperties;
import cn.itcast.demo.mymmorpg.entity.MapConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.model.MonsterWaveSimpleFactory;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneCsReq;
import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SceneActorService 测试夹具：复用进场景/地图配置等 Mock 装配。
 */
public class SceneActorServiceTestHarness {

    @Mock
    private ConfigQueryService configQueryService;
    @Mock
    private PlayerCachePort playerCachePort;
    @Mock
    private ScenePolicy scenePolicy;
    @Mock
    private PlayerNotificationPort playerNotificationPort;
    @Mock
    private SceneEventPublisher sceneEventPublisher;
    @Mock
    private CenterSceneRouter centerSceneRouter;

    private AutoCloseable mocks;
    private SceneRuntimeProperties runtimeProperties;

    public SceneActorService createService() {
        mocks = MockitoAnnotations.openMocks(this);
        runtimeProperties = new SceneRuntimeProperties();
        MigrationTicketService migrationTicketService = new MigrationTicketService();
        SceneReconnectStore reconnectStore = new SceneReconnectStore(runtimeProperties);
        when(centerSceneRouter.isSceneReachable(anyInt())).thenReturn(true);
        when(centerSceneRouter.planMigration(anyInt())).thenAnswer(inv ->
                SceneMigrationPlan.localPlan(inv.getArgument(0), "local", "127.0.0.1", 8089));
        return new SceneActorService(
                configQueryService,
                playerCachePort,
                scenePolicy,
                playerNotificationPort,
                sceneEventPublisher,
                new MonsterWaveSimpleFactory(),
                centerSceneRouter,
                migrationTicketService,
                reconnectStore,
                runtimeProperties);
    }

    public void enterScene(SceneActorService service, long playerId, int sceneId) throws Exception {
        MapConfig map = mapConfig(sceneId, 800, 600, 2);
        Player player = player(playerId, "P15测试", 5);
        when(configQueryService.findMapById(sceneId)).thenReturn(map);
        when(scenePolicy.allowEnterScene(anyInt(), anyLong())).thenReturn(true);
        when(playerCachePort.findById(playerId)).thenReturn(player);
        when(configQueryService.listMonstersForMap(anyInt())).thenReturn(Collections.emptyList());
        lenient().when(configQueryService.listAllMonsters()).thenReturn(Collections.emptyList());
        service.handleEnterScene(playerId, EnterSceneCsReq.newBuilder()
                .setSceneId(sceneId)
                .setLineId(1)
                .build());
    }

    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    private static MapConfig mapConfig(int id, int width, int height, int defaultLines) {
        MapConfig map = new MapConfig();
        map.setId(id);
        map.setWidth(width);
        map.setHeight(height);
        map.setDefaultLines(defaultLines);
        return map;
    }

    private static Player player(long id, String name, int level) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setLevel(level);
        return player;
    }
}
