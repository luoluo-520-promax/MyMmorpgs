/**
 * 文件说明：默认聊天内容策略单元测试。
 * 职责：验证 ChatPolicyConfiguration 中敏感词过滤、空白拒绝与 512 字截断逻辑。
 */
package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.support.ChatPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认 ChatPolicy 单元测试。
 */
public class DefaultChatPolicyTest {

    private static final Logger log = LoggerFactory.getLogger(DefaultChatPolicyTest.class);

    private ChatPolicy chatPolicy;

    @BeforeMethod
    public void setUp() {
        chatPolicy = new ChatPolicyConfiguration().chatPolicy();
        log.info("[测试前置] 默认 ChatPolicy 已加载");
    }

    @Test
    public void filterContent_normalWorldText() {
        int channel = 2;
        int msgType = 1;
        String rawContent = "大家好，世界频道消息";
        log.info("[测试开始] 场景=世界频道正常文本 | channel={} | msgType={} | rawContent={}",
                channel, msgType, rawContent);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=世界频道正常文本 | filtered={} | 期望={}", filtered, rawContent);
        assertThat(filtered).isEqualTo(rawContent);
    }

    @Test
    public void filterContent_trimWhitespace() {
        int channel = 1;
        int msgType = 1;
        String rawContent = "  私聊消息  ";
        String expected = "私聊消息";
        log.info("[测试开始] 场景=首尾空白裁剪 | channel={} | msgType={} | rawContent={}",
                channel, msgType, rawContent);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=首尾空白裁剪 | filtered={} | 期望={}", filtered, expected);
        assertThat(filtered).isEqualTo(expected);
    }

    @Test
    public void filterContent_blockedWordGambling() {
        int channel = 2;
        int msgType = 1;
        String rawContent = "来一局赌博吧";
        log.info("[测试开始] 场景=违禁词赌博 | channel={} | msgType={} | rawContent={}",
                channel, msgType, rawContent);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=违禁词赌博 | filtered={} | 期望=null", filtered);
        assertThat(filtered).isNull();
    }

    @Test
    public void filterContent_blockedWordIllegal() {
        int channel = 3;
        int msgType = 1;
        String rawContent = "传播法轮相关内容";
        log.info("[测试开始] 场景=违禁词法轮 | channel={} | msgType={} | rawContent={}",
                channel, msgType, rawContent);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=违禁词法轮 | filtered={} | 期望=null", filtered);
        assertThat(filtered).isNull();
    }

    @Test
    public void filterContent_blockedWordPorn() {
        int channel = 4;
        int msgType = 1;
        String rawContent = "发送色情链接";
        log.info("[测试开始] 场景=违禁词色情 | channel={} | msgType={} | rawContent={}",
                channel, msgType, rawContent);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=违禁词色情 | filtered={} | 期望=null", filtered);
        assertThat(filtered).isNull();
    }

    @Test
    public void filterContent_nullContent() {
        int channel = 1;
        int msgType = 1;
        String rawContent = null;
        log.info("[测试开始] 场景=null正文拒绝 | channel={} | msgType={} | rawContent=null",
                channel, msgType);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=null正文拒绝 | filtered={} | 期望=null", filtered);
        assertThat(filtered).isNull();
    }

    @Test
    public void filterContent_blankContent() {
        int channel = 2;
        int msgType = 1;
        String rawContent = "   \n\t  ";
        log.info("[测试开始] 场景=纯空白拒绝 | channel={} | msgType={} | rawContent={}",
                channel, msgType, rawContent);

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=纯空白拒绝 | filtered={} | 期望=null", filtered);
        assertThat(filtered).isNull();
    }

    @Test
    public void filterContent_truncateOver512() {
        int channel = 2;
        int msgType = 1;
        String rawContent = "A".repeat(600);
        String expected = "A".repeat(512);
        log.info("[测试开始] 场景=超长截断512字 | channel={} | msgType={} | rawLength={}",
                channel, msgType, rawContent.length());

        String filtered = chatPolicy.filterContent(channel, msgType, rawContent);

        log.info("[测试断言] 场景=超长截断512字 | filteredLength={} | 期望Length={}",
                filtered.length(), expected.length());
        assertThat(filtered).isEqualTo(expected);
    }

    @Test
    public void isChannelUnlocked_defaultAllOpen() {
        long senderPlayerId = 10001L;
        int channel = 3;
        log.info("[测试开始] 场景=默认频道解锁 | senderPlayerId={} | channel={}",
                senderPlayerId, channel);

        boolean unlocked = chatPolicy.isChannelUnlocked(senderPlayerId, channel);

        log.info("[测试断言] 场景=默认频道解锁 | unlocked={} | 期望=true", unlocked);
        assertThat(unlocked).isTrue();
    }
}
