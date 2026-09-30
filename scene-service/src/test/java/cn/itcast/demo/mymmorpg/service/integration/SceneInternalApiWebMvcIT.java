package cn.itcast.demo.mymmorpg.service.integration;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterSceneScRsp;
import cn.itcast.demo.mymmorpg.service.SceneActorService;
import cn.itcast.demo.mymmorpg.service.integration.support.SceneWebMvcTestApplication;
import cn.itcast.demo.mymmorpg.web.InternalSceneController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalSceneController.class)
@ContextConfiguration(classes = SceneWebMvcTestApplication.class)
@TestPropertySource(properties = {
        "spring.application.name=scene-service",
        "game.internal-api.enabled=false"
})
public class SceneInternalApiWebMvcIT extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SceneActorService sceneActorService;

    @Test
    public void enter_returnsProtobufPayload() throws Exception {
        byte[] reqBody = EnterSceneCsReq.newBuilder().setSceneId(1).build().toByteArray();
        byte[] rspBody = EnterSceneScRsp.newBuilder().setRetcode(RetCode.OK).build().toByteArray();
        when(sceneActorService.handleEnterScene(eq(3L), any(EnterSceneCsReq.class)))
                .thenReturn(new ProtocolMessage(MessageId.ENTER_SCENE_SC_RSP, rspBody));

        mockMvc.perform(post("/internal/scene/enter")
                        .header("X-Player-Id", "3")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(reqBody))
                .andExpect(status().isOk());
    }
}
