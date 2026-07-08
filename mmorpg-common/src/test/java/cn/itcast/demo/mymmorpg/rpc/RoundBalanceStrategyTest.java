/**
 * 文件说明：轮询负载均衡策略单元测试。
 * 职责：验证 RoundBalanceStrategy 按顺序选取战斗服会话。
 */
package cn.itcast.demo.mymmorpg.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RoundBalanceStrategyTest {

    private static final Logger log = LoggerFactory.getLogger(RoundBalanceStrategyTest.class);

    private RoundBalanceStrategy strategy;

    @BeforeMethod
    public void setUp() {
        strategy = new RoundBalanceStrategy();
        log.info("[测试前置] RoundBalanceStrategy 已加载");
    }

    @Test
    public void pick_emptySessions_returnsNull() {
        log.info("[测试开始] 场景=空会话池 | sessionsSize=0");

        IdSession picked = strategy.pick(Collections.emptyList());

        log.info("[测试断言] 场景=空会话池 | picked={} | 期望=null", picked);
        assertThat(picked).isNull();
    }

    @Test
    public void pick_rotatesThroughSessions() {
        IdSession s1 = stubSession(101);
        IdSession s2 = stubSession(102);
        IdSession s3 = stubSession(103);
        List<IdSession> sessions = Arrays.asList(s1, s2, s3);
        log.info("[测试开始] 场景=轮询选路 | sessionsSize={} | serverIds=[101,102,103]", sessions.size());

        IdSession first = strategy.pick(sessions);
        IdSession second = strategy.pick(sessions);
        IdSession third = strategy.pick(sessions);
        IdSession fourth = strategy.pick(sessions);

        log.info("[测试断言] 场景=轮询选路 | picks=[{},{},{},{}] | 期望循环101→102→103→101",
                first.getServerId(), second.getServerId(), third.getServerId(), fourth.getServerId());
        assertThat(first.getServerId()).isEqualTo(101);
        assertThat(second.getServerId()).isEqualTo(102);
        assertThat(third.getServerId()).isEqualTo(103);
        assertThat(fourth.getServerId()).isEqualTo(101);
    }

    private static IdSession stubSession(int serverId) {
        return new IdSession() {
            @Override
            public int getServerId() {
                return serverId;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void send(Object message) {
            }

            @Override
            public void close() {
            }
        };
    }
}
