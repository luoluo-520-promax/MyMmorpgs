package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.SessionLoginProperties;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResumeScene 保护期内同设备重连：不踢线，仅 touchHeartbeat。
 */
public class SessionKickReconnectFlowTest {

    private PlayerSessionService playerSessionService;
    private PlayerNotificationPort notificationPort;
    private SceneCommandGateway sceneCommandGateway;
    private SessionKickService.ReconnectGraceQuery reconnectGraceQuery;
    private SessionKickService service;

    @BeforeMethod
    @SuppressWarnings("unchecked")
    public void setUp() {
        playerSessionService = mock(PlayerSessionService.class);
        notificationPort = mock(PlayerNotificationPort.class);
        sceneCommandGateway = mock(SceneCommandGateway.class);
        reconnectGraceQuery = mock(SessionKickService.ReconnectGraceQuery.class);
        ObjectProvider<SessionKickService.ReconnectGraceQuery> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(reconnectGraceQuery);
        SessionLoginProperties props = new SessionLoginProperties();
        props.setReconnectNoKickMs(60_000L);
        service = new SessionKickService(playerSessionService, notificationPort, sceneCommandGateway, props, provider);
    }

    @Test
    public void reconnectGrace_sameDevice_skipsKickAndTouchesHeartbeat() {
        when(playerSessionService.findBoundPlayerId(1001L)).thenReturn(9001L);
        when(reconnectGraceQuery.hasActiveReconnect(9001L)).thenReturn(true);
        when(playerSessionService.getOnlineFields(9001L)).thenReturn(Map.of("device_id", "phone-1"));

        boolean kicked = service.kickPreviousSession(1001L, "phone-1");

        assertThat(kicked).isFalse();
        verify(playerSessionService).touchHeartbeat(9001L);
        verify(notificationPort, never()).kick(anyLong(), anyInt(), anyString());
        verify(sceneCommandGateway, never()).onPlayerLeave(anyLong());
        verify(playerSessionService, never()).markOffline(anyLong());
    }

    @Test
    public void reconnectGrace_differentDevice_stillKicks() {
        when(playerSessionService.findBoundPlayerId(1001L)).thenReturn(9001L);
        when(reconnectGraceQuery.hasActiveReconnect(9001L)).thenReturn(true);
        when(playerSessionService.getOnlineFields(9001L)).thenReturn(Map.of("device_id", "phone-1"));

        boolean kicked = service.kickPreviousSession(1001L, "pc-9");

        assertThat(kicked).isTrue();
        verify(notificationPort).kick(eq(9001L), eq(SessionKickService.REASON_DUPLICATE_LOGIN), anyString());
        verify(sceneCommandGateway).onPlayerLeave(9001L);
        verify(playerSessionService).markOffline(9001L);
    }

    @Test
    public void noReconnectGrace_kicksAsUsual() {
        when(playerSessionService.findBoundPlayerId(1001L)).thenReturn(9001L);
        when(reconnectGraceQuery.hasActiveReconnect(9001L)).thenReturn(false);

        assertThat(service.kickPreviousSession(1001L, "any")).isTrue();
        verify(notificationPort).kick(eq(9001L), eq(1), anyString());
    }
}
