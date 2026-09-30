/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/test/java/cn/itcast/demo/mymmorpg/service/integration/ActivityInternalApiWebMvcIT.java
 * 2) 所属模块：activity-service / test / integration
 * 3) 主要职责：内部活动 HTTP API 的 WebMvc 切片测试（Mock ActivityService）
 * 4) 系统位置：验证 InternalActivityController 路由与 Protobuf 响应格式
 * 5) 变更建议：新增内部端点时在此补充 MockMvc 用例
 */
package cn.itcast.demo.mymmorpg.service.integration;

import cn.itcast.demo.mymmorpg.protocol.ActivityRetCode; // 活动返回码
import cn.itcast.demo.mymmorpg.protocol.MessageId; // 消息号
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // 业务返回封装
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListCsReq; // 列表请求 Protobuf
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetActivityListScRsp; // 列表响应 Protobuf
import cn.itcast.demo.mymmorpg.service.ActivityService; // 被 Mock 的业务层
import cn.itcast.demo.mymmorpg.service.integration.support.ActivityWebMvcTestApplication; // WebMvc 切片专用配置
import cn.itcast.demo.mymmorpg.web.InternalActivityController; // 被测 Controller
import org.springframework.beans.factory.annotation.Autowired; // 注入 MockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest; // WebMvc 切片
import org.springframework.boot.test.mock.mockito.MockBean; // 替换 ActivityService Bean
import org.springframework.http.MediaType; // APPLICATION_OCTET_STREAM
import org.springframework.test.context.ContextConfiguration; // 显式指定 Spring Boot 配置
import org.springframework.test.context.TestPropertySource; // 测试专用属性
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests; // TestNG + Spring
import org.springframework.test.web.servlet.MockMvc; // 模拟 HTTP
import org.testng.annotations.Test; // TestNG 测试

import static org.mockito.ArgumentMatchers.any; // Mockito 任意参数
import static org.mockito.ArgumentMatchers.eq; // Mockito 精确匹配
import static org.mockito.Mockito.when; // Mockito stub
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // POST 请求构建
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status; // HTTP 状态断言

/**
 * 内部活动 HTTP API 的 MVC 层集成（Mock {@link ActivityService}）。
 */
@WebMvcTest(controllers = InternalActivityController.class)
@ContextConfiguration(classes = ActivityWebMvcTestApplication.class) // 避免 IDE 找不到 @SpringBootConfiguration
@TestPropertySource(properties = {
        "spring.application.name=activity-service",
        "game.internal-api.enabled=false"
})
public class ActivityInternalApiWebMvcIT extends AbstractTestNGSpringContextTests { // WebMvc 集成测试

    /** 注入 MockMvc 模拟 HTTP 请求。 */
    @Autowired
    private MockMvc mockMvc; // 模拟 HTTP 客户端

    /** Mock 业务层，避免连接真实 DB。 */
    @MockBean
    private ActivityService activityService; // Mock 业务，避免连 DB

    /**
     * POST /internal/activity/list 应返回 HTTP 200 与 Protobuf 响应体。
     */
    @Test
    public void list_returnsProtobufPayload() throws Exception { // POST /internal/activity/list 返回 200
        byte[] reqBody = GetActivityListCsReq.getDefaultInstance().toByteArray(); // 请求体字节
        byte[] rspBody = GetActivityListScRsp.newBuilder().setRetcode(ActivityRetCode.OK).build().toByteArray(); // 模拟 OK 列表（无活动项）
        when(activityService.handleGetActivityList(eq(3L), any(GetActivityListCsReq.class))) // stub Service
                .thenReturn(new ProtocolMessage(MessageId.GET_ACTIVITY_LIST_SC_RSP, rspBody)); // 返回模拟 ProtocolMessage

        mockMvc.perform(post("/internal/activity/list") // 模拟 Feign 调用
                        .header("X-Player-Id", "3") // 玩家 ID 请求头
                        .contentType(MediaType.APPLICATION_OCTET_STREAM) // 二进制 Content-Type
                        .content(reqBody)) // 请求体
                .andExpect(status().isOk()); // 期望 HTTP 200
    }
}
