package cn.itcast.demo.mymmorpg.cli;

import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.AccountLoginScRsp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 通过 WebSocket 连接真实 player-service 并执行账号登录。
 */
final class ServerConnector {

    private ServerConnector() {
    }

    static int connect(Map<String, String> params) throws Exception {
        String gatewayUrl = CliArgs.getString(params, "url", "ws://127.0.0.1:8989/ws/player");
        String account = CliArgs.getString(params, "account", "testuser");
        String password = CliArgs.getString(params, "password", "123456");
        int timeoutSec = CliArgs.getInt(params, "timeout", 10);

        byte[] loginReq = AccountLoginCsReq.newBuilder()
                .setAccountName(account)
                .setPassword(password)
                .build()
                .toByteArray();
        byte[] frame = encodeFrame(MessageId.ACCOUNT_LOGIN_CS_REQ, loginReq);

        CompletableFuture<byte[]> responseFuture = new CompletableFuture<>();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();

        WebSocket ws = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .buildAsync(URI.create(gatewayUrl), new LoginListener(responseFuture))
                .get(timeoutSec, TimeUnit.SECONDS);

        ws.sendBinary(ByteBuffer.wrap(frame), true).get(timeoutSec, TimeUnit.SECONDS);

        byte[] responsePayload = responseFuture.get(timeoutSec, TimeUnit.SECONDS);
        AccountLoginScRsp rsp = AccountLoginScRsp.parseFrom(responsePayload);

        System.out.printf("连接: %s%n", gatewayUrl);
        System.out.printf("账号: %s%n", account);
        System.out.printf("retcode: %d%n", rsp.getRetcode());
        if (rsp.getRetcode() != 0) {
            System.out.println("登录失败");
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            return 1;
        }
        System.out.printf("accountId: %d%n", rsp.getAccountId());
        System.out.printf("token: %s%n", rsp.getToken());
        System.out.printf("角色数: %d%n", rsp.getPlayerListCount());
        for (int i = 0; i < rsp.getPlayerListCount(); i++) {
            var p = rsp.getPlayerList(i);
            System.out.printf("  - playerId=%d name=%s level=%d%n", p.getPlayerId(), p.getPlayerName(), p.getLevel());
        }
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        return 0;
    }

    private static byte[] encodeFrame(int msgId, byte[] payload) {
        int frameContentLength = 4 + payload.length;
        ByteBuffer out = ByteBuffer.allocate(4 + 4 + payload.length);
        out.order(ByteOrder.BIG_ENDIAN);
        out.putInt(frameContentLength);
        out.putInt(msgId);
        out.put(payload);
        return out.array();
    }

    private static byte[] decodePayload(byte[] frame) {
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN);
        int contentLen = buf.getInt();
        if (contentLen < 4) {
            throw new IllegalArgumentException("帧长度非法");
        }
        buf.getInt(); // msgId
        byte[] payload = new byte[contentLen - 4];
        buf.get(payload);
        return payload;
    }

    private static final class LoginListener implements WebSocket.Listener {

        private final CompletableFuture<byte[]> responseFuture;
        private final ByteBuffer buffer = ByteBuffer.allocate(65536);

        private LoginListener(CompletableFuture<byte[]> responseFuture) {
            this.responseFuture = responseFuture;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            buffer.put(data);
            if (last) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                buffer.clear();
                try {
                    responseFuture.complete(decodePayload(bytes));
                } catch (Exception e) {
                    responseFuture.completeExceptionally(e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            responseFuture.completeExceptionally(error);
        }
    }
}
