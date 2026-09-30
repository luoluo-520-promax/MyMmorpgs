package cn.itcast.demo.mymmorpg.integration;

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.integration.support.PlayerPortWebMvcTestApplication;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.service.PlayerDataLoadStateService;
import cn.itcast.demo.mymmorpg.service.PlayerEntityCacheService;
import cn.itcast.demo.mymmorpg.service.PlayerProgressService;
import cn.itcast.demo.mymmorpg.web.InternalPlayerPortController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.annotations.Test;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPlayerPortController.class)
@ContextConfiguration(classes = PlayerPortWebMvcTestApplication.class)
@TestPropertySource(properties = {
        "spring.application.name=player-service",
        "game.internal-api.enabled=false"
})
public class PlayerPortInternalApiWebMvcIT extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerEntityCacheService playerEntityCacheService;

    @MockBean
    private PlayerNotificationPort playerNotificationPort;

    @MockBean
    private PlayerProgressService playerProgressService;

    @MockBean
    private PlayerDataLoadStateService playerDataLoadStateService;

    @Test
    public void getCache_returnsPlayerJson() throws Exception {
        Player player = new Player();
        player.setId(5L);
        player.setName("hero");
        when(playerEntityCacheService.findById(5L)).thenReturn(player);

        mockMvc.perform(get("/internal/player/cache/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("hero"));
    }

    @Test
    public void notifySend_invokesPort() throws Exception {
        String body = "{\"msgId\":100,\"payload\":\"\"}";
        mockMvc.perform(post("/internal/player/notify/send")
                        .header("X-Player-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(playerNotificationPort).send(7L, 100, new byte[0]);
    }
}
