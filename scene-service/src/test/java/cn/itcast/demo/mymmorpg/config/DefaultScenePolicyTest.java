/**
 * 文件说明：默认场景进入策略单元测试。
 * 职责：验证 ScenePolicyConfiguration 中 allowEnterScene 的 mapId/playerId 校验逻辑。
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.support.ScenePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认 ScenePolicy 单元测试。
 */
public class DefaultScenePolicyTest {

    private static final Logger log = LoggerFactory.getLogger(DefaultScenePolicyTest.class);

    private ScenePolicy scenePolicy;

    @BeforeMethod
    public void setUp() {
        scenePolicy = new ScenePolicyConfiguration().scenePolicy();
        log.info("[测试前置] 默认 ScenePolicy 已加载");
    }

    @Test
    public void allowEnterScene_validParams() {
        int mapId = 1001;
        long playerId = 42L;
        log.info("[测试开始] 场景=允许进场景 | mapId={} | playerId={}", mapId, playerId);

        boolean allowed = scenePolicy.allowEnterScene(mapId, playerId);

        log.info("[测试断言] 场景=允许进场景 | allowed={} | 期望=true", allowed);
        assertThat(allowed).isTrue();
    }

    @Test
    public void allowEnterScene_zeroMapId() {
        int mapId = 0;
        long playerId = 42L;
        log.info("[测试开始] 场景=非法mapId | mapId={} | playerId={}", mapId, playerId);

        boolean allowed = scenePolicy.allowEnterScene(mapId, playerId);

        log.info("[测试断言] 场景=非法mapId | allowed={} | 期望=false", allowed);
        assertThat(allowed).isFalse();
    }

    @Test
    public void allowEnterScene_negativeMapId() {
        int mapId = -3;
        long playerId = 99L;
        log.info("[测试开始] 场景=负值mapId | mapId={} | playerId={}", mapId, playerId);

        boolean allowed = scenePolicy.allowEnterScene(mapId, playerId);

        log.info("[测试断言] 场景=负值mapId | allowed={} | 期望=false", allowed);
        assertThat(allowed).isFalse();
    }

    @Test
    public void allowEnterScene_zeroPlayerId() {
        int mapId = 1001;
        long playerId = 0L;
        log.info("[测试开始] 场景=未选角 | mapId={} | playerId={}", mapId, playerId);

        boolean allowed = scenePolicy.allowEnterScene(mapId, playerId);

        log.info("[测试断言] 场景=未选角 | allowed={} | 期望=false", allowed);
        assertThat(allowed).isFalse();
    }

    @Test
    public void allowEnterScene_negativePlayerId() {
        int mapId = 1001;
        long playerId = -1L;
        log.info("[测试开始] 场景=非法playerId | mapId={} | playerId={}", mapId, playerId);

        boolean allowed = scenePolicy.allowEnterScene(mapId, playerId);

        log.info("[测试断言] 场景=非法playerId | allowed={} | 期望=false", allowed);
        assertThat(allowed).isFalse();
    }
}
