/**
 * 文件说明：内部战斗 HTTP API 的 WebMvc 层集成测试。
 * 职责：使用 @WebMvcTest 切片测试 InternalBattleController，Mock BattleService 验证 Protobuf 请求/响应流程。
 */
package cn.itcast.demo.mymmorpg.service.integration;

import cn.itcast.demo.mymmorpg.service.BattleService; // Mock 业务层
import cn.itcast.demo.mymmorpg.service.integration.support.BattleWebMvcTestApplication; // WebMvc 切片专用配置
import cn.itcast.demo.mymmorpg.web.InternalBattleController; // 被测 Controller
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 协议消息 ID
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 协议消息
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 返回码
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq; // 开始战斗请求
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartScRsp; // 开始战斗响应
import org.springframework.beans.factory.annotation.Autowired; // 自动注入
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest; // WebMvc 切片测试
import org.springframework.boot.test.mock.mockito.MockBean; // Mock Bean
import org.springframework.http.MediaType; // 媒体类型
import org.springframework.test.context.ContextConfiguration; // 显式指定 Spring Boot 配置
import org.springframework.test.context.TestPropertySource; // 测试专用属性
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests; // TestNG Spring 基类
import org.springframework.test.web.servlet.MockMvc; // 模拟 HTTP 客户端
import org.testng.annotations.Test; // 测试注解

import static org.mockito.ArgumentMatchers.any; // 任意参数
import static org.mockito.ArgumentMatchers.eq; // 精确匹配
import static org.mockito.Mockito.when; // stub
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // POST 请求
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // HTTP 状态断言

/**
 * 内部战斗 HTTP API 的 MVC 层集成测试（Mock {@link BattleService}）。
 */
@WebMvcTest(controllers = InternalBattleController.class) // 仅加载 MVC 切片
@ContextConfiguration(classes = BattleWebMvcTestApplication.class) // 避免 IDE 找不到 @SpringBootConfiguration
@TestPropertySource(properties = "spring.application.name=battle-service") // 满足 Controller @ConditionalOnProperty
public class BattleInternalApiWebMvcIT extends AbstractTestNGSpringContextTests { // WebMvc 集成测试

    /** 模拟 HTTP 客户端 */
    @Autowired
    private MockMvc mockMvc; // 模拟 HTTP

    /** Mock 战斗业务服务 */
    @MockBean
    private BattleService battleService; // Mock 战斗业务

    /**
     * 测试：POST /internal/battle/start 应返回 HTTP 200 及 Protobuf 载荷。
     */
    @Test
    public void start_returnsProtobufPayload() throws Exception { // 开始战斗接口测试
        byte[] reqBody = BattleStartCsReq.newBuilder().setLineupId(0).setEnemyId(1).build().toByteArray(); // 请求字节（阵容无效）
        byte[] rspBody = BattleStartScRsp.newBuilder().setRetcode(RetCode.BATTLE_LINEUP_INVALID).build().toByteArray(); // 模拟阵容无效错误
        when(battleService.handleBattleStart(eq(3L), any(BattleStartCsReq.class))) // stub Service
                .thenReturn(new ProtocolMessage(MessageId.BATTLE_START_SC_RSP, rspBody)); // 返回模拟响应

        mockMvc.perform(post("/internal/battle/start") // 模拟 Feign 调用
                        .header("X-Player-Id", "3") // 玩家 ID 请求头
                        .contentType(MediaType.APPLICATION_OCTET_STREAM) // Protobuf 内容类型
                        .content(reqBody)) // 请求体
                .andExpect(status().isOk()); // 断言 HTTP 200
    }
}
