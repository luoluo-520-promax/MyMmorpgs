/**
 * 文件说明：战斗协议消息号 sanity 测试。
 * 职责：确保战斗相关 MessageId 常量符合协议约定。
 */
package cn.itcast.demo.mymmorpg.service.unit;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 战斗协议消息号 sanity 测试。
 */
public class BattleMessageIdSanityTest {

    private static final Logger log = LoggerFactory.getLogger(BattleMessageIdSanityTest.class);

    @Test
    public void battleStartMessageIds() {
        log.info("[测试开始] 场景=开战消息号 | BATTLE_START_CS_REQ={} | BATTLE_START_SC_RSP={}",
                MessageId.BATTLE_START_CS_REQ, MessageId.BATTLE_START_SC_RSP);

        assertThat(MessageId.BATTLE_START_CS_REQ).isEqualTo(201);
        assertThat(MessageId.BATTLE_START_SC_RSP).isEqualTo(202);

        log.info("[测试断言] 场景=开战消息号 | 期望=[201, 202] | 实际=[{}, {}]",
                MessageId.BATTLE_START_CS_REQ, MessageId.BATTLE_START_SC_RSP);
    }

    @Test
    public void battleActionAndEndMessageIds() {
        log.info("[测试开始] 场景=行动/同步/结算消息号 | actionReq={} | actionRsp={} | syncNotify={} | endReq={} | endRsp={}",
                MessageId.BATTLE_ACTION_CS_REQ, MessageId.BATTLE_ACTION_SC_RSP,
                MessageId.BATTLE_SYNC_SC_NOTIFY, MessageId.BATTLE_END_CS_REQ, MessageId.BATTLE_END_SC_RSP);

        assertThat(MessageId.BATTLE_ACTION_CS_REQ).isEqualTo(203);
        assertThat(MessageId.BATTLE_ACTION_SC_RSP).isEqualTo(204);
        assertThat(MessageId.BATTLE_SYNC_SC_NOTIFY).isEqualTo(205);
        assertThat(MessageId.BATTLE_END_CS_REQ).isEqualTo(206);
        assertThat(MessageId.BATTLE_END_SC_RSP).isEqualTo(207);

        log.info("[测试断言] 场景=行动/同步/结算消息号 | 期望=[203,204,205,206,207] | 实际=[{},{},{},{},{}]",
                MessageId.BATTLE_ACTION_CS_REQ, MessageId.BATTLE_ACTION_SC_RSP,
                MessageId.BATTLE_SYNC_SC_NOTIFY, MessageId.BATTLE_END_CS_REQ, MessageId.BATTLE_END_SC_RSP);
    }
}
