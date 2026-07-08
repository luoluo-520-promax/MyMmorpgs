/**
 * 文件说明：Groovy 聊天内容策略单元测试。
 * 职责：验证 GroovyChatPolicy 与默认敏感词过滤逻辑一致。
 */
package cn.itcast.demo.mymmorpg.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Groovy ChatPolicy 单元测试。
 */
public class GroovyChatPolicyTest {

    private static final Logger log = LoggerFactory.getLogger(GroovyChatPolicyTest.class);

    private ChatPolicy chatPolicy;

    @BeforeMethod
    public void setUp() {
        chatPolicy = new GroovyChatPolicy();
        log.info("[测试前置] GroovyChatPolicy 已加载");
    }

    @Test
    public void filterContent_matchesDefaultFormula() {
        int channel = 2;
        int msgType = 1;
        String rawContent = "世界频道 Groovy 过滤测试";
        log.info("[测试开始] 场景=Groovy正常文本 | channel={} | msgType={} | rawContent={}",
                channel, msgType, rawContent);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=Groovy正常文本 | filtered={} | 期望={}", filtered, rawContent);
        assertThat(filtered).isEqualTo(rawContent);
    }

    @Test
    public void filterContent_blockedWordRejected() {
        int channel = 1;
        int msgType = 1;
        String rawContent = "私聊里提到赌博";
        log.info("[测试开始] 场景=Groovy违禁词拒绝 | channel={} | msgType={} | rawContent={}",
                channel, msgType, rawContent);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=Groovy违禁词拒绝 | filtered={} | 期望=null", filtered);
        assertThat(filtered).isNull();
    }

    @Test
    public void isChannelUnlocked_matchesDefault() {
        long senderPlayerId = 20002L;
        int channel = 4;
        log.info("[测试开始] 场景=Groovy频道解锁 | senderPlayerId={} | channel={}",
                senderPlayerId, channel);

        boolean unlocked = chatPolicy.isChannelUnlocked(senderPlayerId, channel);

        log.info("[测试断言] 场景=Groovy频道解锁 | unlocked={} | 期望=true", unlocked);
        assertThat(unlocked).isTrue();
    }
}
