/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/ObservabilityWebSocketService.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：装饰 WebSocketService，统计 WS 会话收发消息数与字节数并打 GW_WS 日志。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route; // 从 exchange 属性解析 WS 路由 id 与目标 URI
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.socket.WebSocketHandler; // 业务 WS 处理器，被 ObservedSession 包装
import org.springframework.web.reactive.socket.WebSocketMessage; // TEXT/BINARY/PING/PONG 消息载体
import org.springframework.web.reactive.socket.WebSocketSession; // WS 连接会话，receive/send 被拦截统计
import org.springframework.web.reactive.socket.server.WebSocketService; // Spring Gateway 默认 WS 升级服务，本类为其装饰器
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong; // 会话期间跨线程累加 in/out 消息与字节
import java.util.function.Function;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;


@SuppressWarnings("null")
public class ObservabilityWebSocketService implements WebSocketService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityWebSocketService.class);

    private static final String TRACE_HEADER = "X-Trace-Id";

    /** Spring 默认的 WebSocketService 实现，实际完成 HTTP 升级到 WS 及帧转发 */
    private final WebSocketService delegate;

    /** 观测开关、WS 开关、采样率、日志级别 */
    private final GatewayObserveProperties props;

    public ObservabilityWebSocketService(WebSocketService delegate, GatewayObserveProperties props) {
        this.delegate = delegate;
        this.props = props;
    }

    /**
     * WS 握手入口：采样命中时用 ObservedSession 包装会话，连接关闭后输出 GW_WS 汇总日志。
     */
    @Override
    public Mono<Void> handleRequest(ServerWebExchange exchange, WebSocketHandler handler) {
        if (!props.isEnabled() || !props.isWsEnabled() || !sampled(props.getSampleRate())) {
            return delegate.handleRequest(exchange, handler); // 未启用观测时直接委托
        }
        long startNs = System.nanoTime(); // 从握手到连接关闭的总耗时
        AtomicLong inMsgs = new AtomicLong();   // 客户端 -> 网关 消息条数
        AtomicLong inBytes = new AtomicLong();  // 客户端 -> 网关  payload 字节
        AtomicLong outMsgs = new AtomicLong();  // 网关 -> 客户端 消息条数
        AtomicLong outBytes = new AtomicLong(); // 网关 -> 客户端 payload 字节

        // 将原始 handler 包一层：传入的 session 已是 ObservedSession，receive/send 会被计数
        WebSocketHandler wrapped = session -> handler.handle(new ObservedSession(session, inMsgs, inBytes, outMsgs, outBytes));
        return delegate.handleRequest(exchange, wrapped)
                .doFinally(st -> logSummary(exchange, startNs, inMsgs.get(), inBytes.get(), outMsgs.get(), outBytes.get()));
    }

    /** 连接结束时输出 GW_WS 结构化日志，含 traceId、path、routeId、latencyMs、收发统计 */
    private void logSummary(ServerWebExchange exchange, long startNs,
                            long inMsgs, long inBytes, long outMsgs, long outBytes) {
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
        ServerHttpRequest req = exchange.getRequest();
        String traceId = req.getHeaders().getFirst(TRACE_HEADER);
        String path = req.getURI().getPath(); // 如 /ws

        Object route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        String routeId = null;
        URI routeUri = null;
        if (route instanceof Route r) {
            routeId = r.getId();
            routeUri = r.getUri();
        }

        boolean debug = "DEBUG".equalsIgnoreCase(props.getLogLevel());
        if (debug) {
            log.debug("GW_WS traceId={} path={} routeId={} uri={} latencyMs={} inMsgs={} inBytes={} outMsgs={} outBytes={}",
                    traceId, path, routeId, routeUri, latencyMs, inMsgs, inBytes, outMsgs, outBytes);
        } else {
            log.info("GW_WS traceId={} path={} routeId={} latencyMs={} inMsgs={} inBytes={} outMsgs={} outBytes={}",
                    traceId, path, routeId, latencyMs, inMsgs, inBytes, outMsgs, outBytes);
        }
    }

    /** 与 HttpResponseObserveGlobalFilter 相同的采样逻辑 */
    private static boolean sampled(double rate) {
        if (rate >= 1.0d) {
            return true;
        }
        if (rate <= 0.0d) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    /**
     * WebSocketSession 装饰器：receive/send 路径上统计消息数与 payload 字节，其余方法原样委托。
     */
    private static final class ObservedSession implements WebSocketSession {

        private final WebSocketSession delegate;

        private final AtomicLong inMsgs;

        private final AtomicLong inBytes;

        private final AtomicLong outMsgs;

        private final AtomicLong outBytes;

        private ObservedSession(WebSocketSession delegate,
                                AtomicLong inMsgs, AtomicLong inBytes,
                                AtomicLong outMsgs, AtomicLong outBytes) {
            this.delegate = delegate;
            this.inMsgs = inMsgs;
            this.inBytes = inBytes;
            this.outMsgs = outMsgs;
            this.outBytes = outBytes;
        }

        /** 会话唯一 ID，用于调试，直接透传底层 session */
        @Override
        public String getId() {
            return delegate.getId();
        }

        /** 握手信息（请求 URI、选中的子协议等） */
        @Override
        public HandshakeInfo getHandshakeInfo() {
            return delegate.getHandshakeInfo();
        }

        /** 分配 DataBuffer 的工厂，二进制帧构造时使用 */
        @Override
        public DataBufferFactory bufferFactory() {
            return delegate.bufferFactory();
        }

        /** 会话级属性 Map，与底层 session 共享 */
        @Override
        public Map<String, Object> getAttributes() {
            return delegate.getAttributes();
        }

        /**
         * 订阅客户端入站消息流：每收到一帧 inMsgs+1，payload 字节累加到 inBytes。
         */
        @Override
        public Flux<WebSocketMessage> receive() {
            return delegate.receive().doOnNext(msg -> {
                inMsgs.incrementAndGet();
                inBytes.addAndGet(payloadBytes(msg));
            });
        }

        /**
         * 向客户端发送出站消息：每发出一帧 outMsgs+1，payload 字节累加到 outBytes。
         */
        @Override
        public Mono<Void> send(org.reactivestreams.Publisher<WebSocketMessage> messages) {
            Flux<WebSocketMessage> flux = Flux.from(messages).doOnNext(msg -> {
                outMsgs.incrementAndGet();
                outBytes.addAndGet(payloadBytes(msg));
            });
            return delegate.send(flux);
        }

        /** 连接是否仍处于 OPEN 状态 */
        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        /** 主动关闭连接并指定 CloseStatus 码 */
        @Override
        public Mono<Void> close(org.springframework.web.reactive.socket.CloseStatus status) {
            return delegate.close(status);
        }

        /** 监听对端关闭事件，返回 CloseStatus Mono */
        @Override
        public Mono<org.springframework.web.reactive.socket.CloseStatus> closeStatus() {
            return delegate.closeStatus();
        }

        /** 构造 TEXT 类型 WebSocketMessage */
        @Override
        public WebSocketMessage textMessage(String payload) {
            return delegate.textMessage(payload);
        }

        /** 构造 BINARY 类型 WebSocketMessage，由 payloadFactory 提供 DataBuffer */
        @Override
        public WebSocketMessage binaryMessage(Function<org.springframework.core.io.buffer.DataBufferFactory, org.springframework.core.io.buffer.DataBuffer> payloadFactory) {
            return delegate.binaryMessage(payloadFactory);
        }

        /** 构造 PING 心跳帧 */
        @Override
        public WebSocketMessage pingMessage(Function<org.springframework.core.io.buffer.DataBufferFactory, org.springframework.core.io.buffer.DataBuffer> payloadFactory) {
            return delegate.pingMessage(payloadFactory);
        }

        /** 构造 PONG 响应帧 */
        @Override
        public WebSocketMessage pongMessage(Function<org.springframework.core.io.buffer.DataBufferFactory, org.springframework.core.io.buffer.DataBuffer> payloadFactory) {
            return delegate.pongMessage(payloadFactory);
        }

        /** 读取 WebSocketMessage 中 DataBuffer 的可读字节数，空消息返回 0 */
        private static long payloadBytes(WebSocketMessage msg) {
            if (msg == null || msg.getPayload() == null) {
                return 0;
            }
            return msg.getPayload().readableByteCount();
        }
    }
}
