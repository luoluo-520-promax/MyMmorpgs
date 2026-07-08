/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/test/java/cn/itcast/demo/mymmorpg/service/ActivityFacadeTest.java
 * 2) 所属模块：player-service / test/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：测试类 ActivityFacadeTest，验证相关业务逻辑。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.handler.DispatchSession; // 模拟会话
import cn.itcast.demo.mymmorpg.protocol.GamePackets; // 外层协议包装
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 消息?
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 业务返回
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq; // 列表请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListScRsp; // 列表响应
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

public class ActivityFacadeTest { // player-service ?ActivityFacade 单元测试

    @Mock
    private ActivityCommandGateway activityCommandGateway; // 模拟网关（本?远程路由?
    @Mock
    private PlayerDataAsyncPreloadService preloadService; // 模拟预加?
    @Mock
    private DispatchSession session; // 模拟玩家会话

    private AutoCloseable mocks; // Mockito 资源
    private ActivityFacade activityFacade; // 被测 Facade

    @BeforeMethod
    public void setUp() { // 每个测试前初始化
        mocks = MockitoAnnotations.openMocks(this);
        activityFacade = new ActivityFacade(activityCommandGateway, preloadService);
    }

    @AfterMethod
    public void tearDown() throws Exception { // 释放 mock
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void list_delegatesToActivityCommandGateway() throws Exception { // 列表请求应委?Gateway
        GetActivityListCsReq inner = GetActivityListCsReq.getDefaultInstance(); // ?Protobuf 请求
        ProtocolMessage rsp = new ProtocolMessage(
                MessageId.GET_ACTIVITY_LIST_SC_RSP,
                GetActivityListScRsp.newBuilder().setRetcode(0).setLoading(false).build().toByteArray()); // 模拟?loading 的空活动列表
        when(session.playerId()).thenReturn(100L); // stub 玩家 ID
        when(activityCommandGateway.handleGetActivityList(eq(100L), any(GetActivityListCsReq.class))).thenReturn(rsp);

        ProtocolMessage msg = activityFacade.list(session, new GamePackets.GetActivityListCsReq(inner.toByteArray())); // 调用 Facade

        assertThat(msg).isSameAs(rsp); // 应返?Gateway 结果
        verify(activityCommandGateway).handleGetActivityList(eq(100L), any(GetActivityListCsReq.class)); // 验证委托
    }
}
