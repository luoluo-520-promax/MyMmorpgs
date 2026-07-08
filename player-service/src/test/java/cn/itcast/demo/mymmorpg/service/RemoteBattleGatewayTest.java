/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/test/java/cn/itcast/demo/mymmorpg/service/RemoteBattleGatewayTest.java
 * 2) 所属模块：player-service / test/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：测试类 RemoteBattleGatewayTest，验证相关业务逻辑。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.BattleCommandClient; // Feign 客户?mock
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 响应 msgId
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq; // 请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartScRsp; // 响应 payload 构?
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod; // TestNG 测试后置
import org.testng.annotations.BeforeMethod; // TestNG 测试前置
import org.testng.annotations.Test; // 单元测试方法

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RemoteBattleGatewayTest { // RemoteBattleGateway 单元测试

    @Mock
    private BattleCommandClient battleCommandClient; // 模拟 Feign

    private AutoCloseable mocks;
    private RemoteBattleGateway gateway; // 被测：远程战斗网?

    @BeforeMethod
    public void setUp() { // 初始?mock 与被测对?
        mocks = MockitoAnnotations.openMocks(this);
        gateway = new RemoteBattleGateway(battleCommandClient);
    }

    @AfterMethod
    public void tearDown() throws Exception { // 释放 Mockito
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void handleBattleStart_wrapsFeignResponse() throws Exception { // 开始战斗：Feign 字节 -> ProtocolMessage
        BattleStartCsReq req = BattleStartCsReq.newBuilder().setLineupId(1).setEnemyId(2).build(); // 请求?
        byte[] rspPayload = BattleStartScRsp.newBuilder().setRetcode(0).build().toByteArray(); // 模拟 battle-service 返回成功开局（无 battleId 等字段）
        when(battleCommandClient.start(eq(5L), eq(req.toByteArray()))).thenReturn(rspPayload); // stub Feign

        var msg = gateway.handleBattleStart(5L, req); // 调用网关

        assertThat(msg.msgId()).isEqualTo(MessageId.BATTLE_START_SC_RSP); // 网关应补上正?msgId
        assertThat(msg.payload()).isEqualTo(rspPayload); // payload ?Feign 一?
        verify(battleCommandClient).start(eq(5L), eq(req.toByteArray())); // 验证 Feign 被调用且参数正确
    }
}
