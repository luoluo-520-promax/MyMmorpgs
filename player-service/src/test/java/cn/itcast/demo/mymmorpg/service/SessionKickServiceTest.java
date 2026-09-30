package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kick 协议推送：重复登录踢旧会话（msgId=15 经 PlayerNotificationPort.kick）。
 */
public class SessionKickServiceTest {

    private PlayerSessionService playerSessionService;
    private PlayerNotificationPort playerNotificationPort;
    private SceneCommandGateway sceneCommandGateway;
    private SessionKickService service;

    @BeforeMethod
    public void setUp() {
        playerSessionService = mock(PlayerSessionService.class);
        playerNotificationPort = mock(PlayerNotificationPort.class);
        sceneCommandGateway = mock(SceneCommandGateway.class);
        service = new SessionKickService(playerSessionService, playerNotificationPort, sceneCommandGateway);
    }

    @Test
    public void kickPreviousSession_invalidAccount_noop() {
        service.kickPreviousSession(0L);
        verifyNoInteractions(playerSessionService, playerNotificationPort, sceneCommandGateway);
    }

    @Test
    public void kickPreviousSession_noBoundPlayer_noop() {
        when(playerSessionService.findBoundPlayerId(1001L)).thenReturn(null);
        service.kickPreviousSession(1001L);
        verify(playerNotificationPort, never()).kick(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void kickPreviousSession_kicksAndClearsScene() {
        when(playerSessionService.findBoundPlayerId(1001L)).thenReturn(9001L);

        service.kickPreviousSession(1001L);

        verify(playerNotificationPort).kick(9001L, SessionKickService.REASON_DUPLICATE_LOGIN, "账号在其他设备登录");
        verify(sceneCommandGateway).onPlayerLeave(9001L);
        verify(playerSessionService).markOffline(9001L);
        verify(playerSessionService).clearAccountPlayer(1001L);
    }
}
