package cn.itcast.demo.mymmorpg.rpc;

import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * scene-service 侧 RSocket 服务端：承接物理哈希校验等高频内部 RPC。
 */
@Component
@ConditionalOnProperty(name = "game.rpc.physics-rsocket.enabled", havingValue = "true")
public class InternalPhysicsRsocketServer {

    private static final Logger log = LoggerFactory.getLogger(InternalPhysicsRsocketServer.class);

    private final InternalPhysicsRpcBridge bridge;
    private final int port;
    private CloseableChannel server;

    public InternalPhysicsRsocketServer(
            InternalPhysicsRpcBridge bridge,
            @Value("${game.rpc.physics-rsocket.port:7000}") int port) {
        this.bridge = bridge;
        this.port = port;
    }

    @PostConstruct
    public void start() {
        server = RSocketServer.create(SocketAcceptor.forRequestResponse(payload -> {
                    String data = payload.getDataUtf8();
                    return Mono.just(DefaultPayload.create(bridge.handle(data)));
                }))
                .bindNow(TcpServerTransport.create(port));
        log.info("[hyc] InternalPhysicsRsocketServer started on port {}", port);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.dispose();
        }
    }
}
