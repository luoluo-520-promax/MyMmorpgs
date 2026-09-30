package cn.itcast.demo.mymmorpg.rpc;

import io.rsocket.Payload;
import io.rsocket.core.RSocketClient;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RSocket 二进制 RPC 客户端：替换 OpenFeign 调用 /internal/scene/physics/hash/validate。
 * 默认关闭；开启后走 Request-Response，失败回退本地 fallback。
 */
@Component
public class InternalPhysicsRsocketClient implements InternalPhysicsRpcPort {

    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private volatile RSocketClient client;
    private final String host;
    private final int port;
    private final boolean enabled;

    public InternalPhysicsRsocketClient(
            @Value("${game.rpc.physics-rsocket.enabled:false}") boolean enabled,
            @Value("${game.rpc.physics-rsocket.host:127.0.0.1}") String host,
            @Value("${game.rpc.physics-rsocket.port:7000}") int port) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
    }

    private RSocketClient client() {
        if (!enabled) {
            return null;
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = RSocketClient.from(
                            RSocketConnector.create()
                                    .connect(TcpClientTransport.create(host, port)));
                }
            }
        }
        return client;
    }

    @Override
    public CompletableFuture<Map<String, Object>> validateHashAsync(PhysicsValidateReq req) {
        calls.incrementAndGet();
        RSocketClient c = client();
        if (c == null) {
            return CompletableFuture.completedFuture(fallback(req));
        }
        String payload = encodeReq(req);
        return c.requestResponse(Mono.just(DefaultPayload.create(payload)))
                .map(this::decodeRsp)
                .onErrorResume(e -> {
                    errors.incrementAndGet();
                    return Mono.just(fallback(req));
                })
                .toFuture();
    }

    @Override
    public Map<String, Object> validateHash(PhysicsValidateReq req) {
        try {
            return validateHashAsync(req).get();
        } catch (Exception e) {
            errors.incrementAndGet();
            return fallback(req);
        }
    }

    private String encodeReq(PhysicsValidateReq req) {
        return req.playerId() + "|" + req.physicsStateHash() + "|"
                + req.vx() + "|" + req.vy() + "|" + req.vz() + "|"
                + req.gravityScale() + "|" + req.onIceSurface();
    }

    private Map<String, Object> decodeRsp(Payload p) {
        String raw = p.getDataUtf8();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", raw.contains("ok=true"));
        m.put("rsocket", true);
        m.put("raw", raw);
        return m;
    }

    private Map<String, Object> fallback(PhysicsValidateReq req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("rsocket", false);
        m.put("fallback", true);
        m.put("playerId", req.playerId());
        return m;
    }

    public Map<String, Object> stats() {
        return Map.of(
                "enabled", enabled,
                "host", host,
                "port", port,
                "calls", calls.get(),
                "errors", errors.get());
    }
}
