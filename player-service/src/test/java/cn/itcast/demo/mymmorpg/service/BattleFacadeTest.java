/**
 * 文件维护说明
 * 1) 文件路径：src/test/java/cn/itcast/demo/mymmorpg/service/BattleFacadeTest.java
 * 2) 所属模块：player-service（单元测试）
 * 3) 主要职责：验?BattleFacade 委托 BattleCommandGateway 处理战斗协议? */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.handler.DispatchSession;
import cn.itcast.demo.mymmorpg.protocol.GamePackets;
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 协议消息 ID 常量
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 统一协议消息封装（msgId + payload）
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod; // TestNG 测试后置
import org.testng.annotations.BeforeMethod; // TestNG 测试前置
import org.testng.annotations.Test; // 单元测试方法

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BattleFacadeTest {

    @Mock
    private BattleCommandGateway battleCommandGateway;
    @Mock
    private DispatchSession session;

    private AutoCloseable mocks;
    private BattleFacade battleFacade;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        battleFacade = new BattleFacade(battleCommandGateway);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void start_delegatesToBattleCommandGateway() throws Exception {
        BattleStartCsReq inner = BattleStartCsReq.newBuilder().setLineupId(2).setEnemyId(3).build(); // 完成 Protobuf 消息构建
        ProtocolMessage rsp = new ProtocolMessage(MessageId.BATTLE_START_SC_RSP, new byte[] {9});
        when(session.playerId()).thenReturn(100L);
        when(battleCommandGateway.handleBattleStart(eq(100L), any(BattleStartCsReq.class))).thenReturn(rsp);

        ProtocolMessage msg = battleFacade.start(session, new GamePackets.BattleStartCsReq(inner.toByteArray()));

        assertThat(msg).isSameAs(rsp);
        verify(battleCommandGateway).handleBattleStart(eq(100L), any(BattleStartCsReq.class));
    }
}
