/**
 * 文件说明：跨服消息发送工具单元测试。
 * 职责：验证 CrossMessageUtil 在中心服会话可用/不可用时的发送行为。
 */
package cn.itcast.demo.mymmorpg.rpc;

import jforgame.commons.eventbus.EventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CrossMessageUtilTest {

    private static final Logger log = LoggerFactory.getLogger(CrossMessageUtilTest.class);

    private RpcClientRouter router;
    private CrossMessageUtil crossMessageUtil;

    @BeforeMethod
    public void setUp() {
        router = new RpcClientRouter(new EventBus());
        crossMessageUtil = new CrossMessageUtil(router);
        log.info("[测试前置] CrossMessageUtil 已加载");
    }

    @Test
    public void requestToCenter_sendsWhenSessionActive() {
        Object message = new Rpc_G2C_FetchFightServerNodes();
        IdSession center = mock(IdSession.class);
        when(center.isActive()).thenReturn(true);
        router.setCenterSession(center);
        log.info("[测试开始] 场景=中心服活跃 | messageClass={} | centerActive=true",
                message.getClass().getSimpleName());

        crossMessageUtil.requestToCenter(message);

        log.info("[测试断言] 场景=中心服活跃 | 期望=调用center.send一次");
        verify(center).send(message);
    }

    @Test
    public void requestToCenter_skipsWhenSessionInactive() {
        Object message = new Rpc_G2C_FetchFightServerNodes();
        IdSession center = mock(IdSession.class);
        when(center.isActive()).thenReturn(false);
        router.setCenterSession(center);
        log.info("[测试开始] 场景=中心服未激活 | messageClass={} | centerActive=false",
                message.getClass().getSimpleName());

        crossMessageUtil.requestToCenter(message);

        log.info("[测试断言] 场景=中心服未激活 | 期望=不调用center.send");
        verify(center, never()).send(message);
    }

    @Test
    public void requestToCenter_skipsWhenNoCenterSession() {
        Object message = new Rpc_G2C_FetchFightServerNodes();
        log.info("[测试开始] 场景=无中心服会话 | messageClass={} | centerSession=null",
                message.getClass().getSimpleName());

        crossMessageUtil.requestToCenter(message);

        log.info("[测试断言] 场景=无中心服会话 | 期望=静默跳过 | centerSession={}", router.getCenterSession());
        assertThat(router.getCenterSession()).isNull();
    }
}
