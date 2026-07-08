/**
 * 文件说明：WebSocket 二进制帧发送器单元测试。
 * 职责：验证 BinaryFrameSender 的帧格式 [length:4][msgId:4][payload] 及空会话防护。
 */
package cn.itcast.demo.mymmorpg.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BinaryFrameSender 单元测试。
 */
public class BinaryFrameSenderTest {

    private static final Logger log = LoggerFactory.getLogger(BinaryFrameSenderTest.class);

    @Test
    public void sendWebSocket_nullSession_doesNotSend() throws Exception {
        int msgId = 1001;
        byte[] payload = new byte[]{0x0A, 0x0B};
        log.info("[测试开始] 场景=空会话不发送 | session=null | msgId={} | payloadLength={}",
                msgId, payload.length);

        BinaryFrameSender.sendWebSocket(null, msgId, payload);

        log.info("[测试断言] 场景=空会话不发送 | 期望=不调用 sendMessage");
        // null session 无法 verify，仅验证不抛异常即可
    }

    @Test
    public void sendWebSocket_closedSession_doesNotSend() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        int msgId = 2002;
        byte[] payload = new byte[]{0x01};
        log.info("[测试开始] 场景=已关闭会话不发送 | sessionOpen={} | msgId={} | payloadLength={}",
                false, msgId, payload.length);

        BinaryFrameSender.sendWebSocket(session, msgId, payload);

        log.info("[测试断言] 场景=已关闭会话不发送 | 期望=sendMessage 未被调用");
        verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void sendWebSocket_openSession_sendsBigEndianFrame() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        int msgId = 3003;
        byte[] payload = new byte[]{0x11, 0x22, 0x33};
        int expectedContentLength = 4 + payload.length;
        log.info("[测试开始] 场景=开放会话发送二进制帧 | sessionOpen={} | msgId={} | payloadLength={} | expectedContentLength={}",
                true, msgId, payload.length, expectedContentLength);

        BinaryFrameSender.sendWebSocket(session, msgId, payload);

        org.mockito.ArgumentCaptor<WebSocketMessage<?>> captor =
                org.mockito.ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        BinaryMessage message = (BinaryMessage) captor.getValue();
        ByteBuffer buf = message.getPayload().asReadOnlyBuffer();
        buf.order(ByteOrder.BIG_ENDIAN);
        int contentLength = buf.getInt();
        int actualMsgId = buf.getInt();
        byte[] actualPayload = new byte[payload.length];
        buf.get(actualPayload);

        log.info("[测试断言] 场景=开放会话发送二进制帧 | contentLength={} | actualMsgId={} | actualPayload={} | 期望contentLength={} | 期望msgId={}",
                contentLength, actualMsgId, bytesToHex(actualPayload), expectedContentLength, msgId);
        assertThat(contentLength).isEqualTo(expectedContentLength);
        assertThat(actualMsgId).isEqualTo(msgId);
        assertThat(actualPayload).containsExactly(payload);
        assertThat(buf.hasRemaining()).isFalse();
    }

    @Test
    public void sendWebSocket_emptyPayload_sendsLengthFour() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        int msgId = 4004;
        byte[] payload = new byte[0];
        log.info("[测试开始] 场景=空负载帧 | sessionOpen={} | msgId={} | payloadLength={}",
                true, msgId, payload.length);

        BinaryFrameSender.sendWebSocket(session, msgId, payload);

        org.mockito.ArgumentCaptor<WebSocketMessage<?>> captor =
                org.mockito.ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(captor.capture());
        BinaryMessage message = (BinaryMessage) captor.getValue();
        ByteBuffer buf = message.getPayload().asReadOnlyBuffer();
        buf.order(ByteOrder.BIG_ENDIAN);
        int contentLength = buf.getInt();
        int actualMsgId = buf.getInt();

        log.info("[测试断言] 场景=空负载帧 | contentLength={} | actualMsgId={} | 期望contentLength=4 | 期望msgId={}",
                contentLength, actualMsgId, msgId);
        assertThat(contentLength).isEqualTo(4);
        assertThat(actualMsgId).isEqualTo(msgId);
        assertThat(buf.hasRemaining()).isFalse();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
