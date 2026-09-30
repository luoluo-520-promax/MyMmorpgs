package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.net.GameMessage;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.protobuf.KickPlayerScNotify;
import cn.itcast.demo.mymmorpg.support.DispatchFailureReporter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kick（msgId=15）推送关闭连接；Netty 不可写时背压丢弃。
 */
public class PlayerPushRegistryKickBackpressureTest {

    private DispatchFailureReporter failureReporter;
    private PlayerPushRegistry registry;

    @BeforeMethod
    public void setUp() {
        failureReporter = mock(DispatchFailureReporter.class);
        registry = new PlayerPushRegistry(failureReporter);
    }

    @Test
    public void kick_sendsMsgId15AndClosesWebSocket() throws Exception {
        long playerId = 42L;
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.isOpen()).thenReturn(true);
        registry.bindWebSocket(playerId, ws);

        registry.kick(playerId, SessionKickService.REASON_DUPLICATE_LOGIN, "账号在其他设备登录");

        org.mockito.ArgumentCaptor<org.springframework.web.socket.WebSocketMessage<?>> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage.class);
        verify(ws).sendMessage(captor.capture());
        java.nio.ByteBuffer buf = ((org.springframework.web.socket.BinaryMessage) captor.getValue())
                .getPayload().asReadOnlyBuffer();
        buf.order(java.nio.ByteOrder.BIG_ENDIAN);
        buf.getInt(); // length
        int msgId = buf.getInt();
        assertThat(msgId).isEqualTo(MessageId.KICK_PLAYER_SC_NOTIFY);
        assertThat(msgId).isEqualTo(15);

        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        KickPlayerScNotify notify = KickPlayerScNotify.parseFrom(payload);
        assertThat(notify.getReason()).isEqualTo(SessionKickService.REASON_DUPLICATE_LOGIN);
        assertThat(notify.getMessage()).isEqualTo("账号在其他设备登录");

        verify(ws).close(CloseStatus.NORMAL);
        assertThat(registry.isOnline(playerId)).isFalse();
    }

    @Test
    public void send_unwritableNetty_dropsPush() {
        long playerId = 7L;
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.isActive()).thenReturn(true);
        when(channel.isWritable()).thenReturn(false);
        registry.bindNetty(playerId, ctx);

        registry.send(playerId, MessageId.CHAT_MSG_SC_NOTIFY, new byte[]{1, 2});

        verify(ctx, never()).writeAndFlush(any(GameMessage.class));
        verify(failureReporter).recordPushFailure();
    }
}
