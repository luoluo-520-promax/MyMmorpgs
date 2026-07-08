/**
 * 文件说明：默认道具效果解析策略单元测试。
 * 职责：验证 BagPolicyConfiguration 中 ItemPolicy 对 effect_params JSON 的解析逻辑。
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.entity.ItemConfig;
import cn.itcast.demo.mymmorpg.support.ItemPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认 ItemPolicy 单元测试。
 */
public class DefaultItemPolicyTest {

    private static final Logger log = LoggerFactory.getLogger(DefaultItemPolicyTest.class);

    private ItemPolicy itemPolicy;

    @BeforeMethod
    public void setUp() {
        itemPolicy = new BagPolicyConfiguration().itemPolicy();
        log.info("[测试前置] 默认 ItemPolicy 已加载");
    }

    @Test
    public void parseExpReward_validJson() {
        String effectParams = "{\"exp\":150,\"hp\":50}";
        ItemConfig config = itemConfig(1001, effectParams);
        log.info("[测试开始] 场景=解析经验奖励 | itemId={} | effectParams={}", config.getId(), effectParams);

        int exp = itemPolicy.parseExpReward(config);

        log.info("[测试断言] 场景=解析经验奖励 | exp={} | 期望={}", exp, 150);
        assertThat(exp).isEqualTo(150);
    }

    @Test
    public void parseHpRestore_validJson() {
        String effectParams = "{\"exp\":100,\"hp\":320,\"mp\":80}";
        ItemConfig config = itemConfig(1002, effectParams);
        log.info("[测试开始] 场景=解析HP回复 | itemId={} | effectParams={}", config.getId(), effectParams);

        int hp = itemPolicy.parseHpRestore(config);

        log.info("[测试断言] 场景=解析HP回复 | hp={} | 期望={}", hp, 320);
        assertThat(hp).isEqualTo(320);
    }

    @Test
    public void parseMpRestore_validJson() {
        String effectParams = "{\"mp\":200}";
        ItemConfig config = itemConfig(1003, effectParams);
        log.info("[测试开始] 场景=解析MP回复 | itemId={} | effectParams={}", config.getId(), effectParams);

        int mp = itemPolicy.parseMpRestore(config);

        log.info("[测试断言] 场景=解析MP回复 | mp={} | 期望={}", mp, 200);
        assertThat(mp).isEqualTo(200);
    }

    @Test
    public void parseExpReward_nullConfig() {
        log.info("[测试开始] 场景=空配置解析经验 | config=null");

        int exp = itemPolicy.parseExpReward(null);

        log.info("[测试断言] 场景=空配置解析经验 | exp={} | 期望=0", exp);
        assertThat(exp).isZero();
    }

    @Test
    public void parseHpRestore_blankEffectParams() {
        ItemConfig config = itemConfig(1004, "   ");
        log.info("[测试开始] 场景=空白effectParams | itemId={} | effectParams=\"{}\"", config.getId(), config.getEffectParams());

        int hp = itemPolicy.parseHpRestore(config);

        log.info("[测试断言] 场景=空白effectParams | hp={} | 期望=0", hp);
        assertThat(hp).isZero();
    }

    @Test
    public void parseMpRestore_missingField() {
        String effectParams = "{\"exp\":500}";
        ItemConfig config = itemConfig(1005, effectParams);
        log.info("[测试开始] 场景=缺失mp字段 | itemId={} | effectParams={}", config.getId(), effectParams);

        int mp = itemPolicy.parseMpRestore(config);

        log.info("[测试断言] 场景=缺失mp字段 | mp={} | 期望=0", mp);
        assertThat(mp).isZero();
    }

    private static ItemConfig itemConfig(int itemId, String effectParams) {
        ItemConfig config = new ItemConfig();
        config.setId(itemId);
        config.setEffectParams(effectParams);
        return config;
    }
}
