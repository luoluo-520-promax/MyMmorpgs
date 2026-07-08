/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/HttpResponseObserveGlobalFilter.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：装饰 HTTP 响应流，记录延迟、状态码、响应字节数等观测日志。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.reactivestreams.Publisher;
import org.slf4j.Logger; // 输出 GW_HTTP 结构化观测日志
import org.slf4j.LoggerFactory; // 按类名创建 Logger 实例
import org.springframework.cloud.gateway.filter.GatewayFilterChain; // 装饰响应后继续路由转发
import org.springframework.cloud.gateway.filter.GlobalFilter; // 对所有 HTTP 路由响应生效
import org.springframework.core.Ordered; // order=-60，在认证/限流之后、业务响应返回前装饰
import org.springframework.core.io.buffer.DataBuffer; // Netty 字节缓冲区，统计 readableByteCount 得响应体大小
import org.springframework.http.HttpStatusCode; // 读取最终 HTTP 状态码写入日志
import org.springframework.http.server.reactive.ServerHttpResponse; // 原始响应，被 Decorator 包装
import org.springframework.http.server.reactive.ServerHttpResponseDecorator; // 拦截 writeWith 统计出站字节
import org.springframework.lang.NonNull; // 满足父类方法非空契约
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange; // 含 GATEWAY_ROUTE_ATTR 路由元数据
import reactor.core.publisher.Flux; // 响应体多为 Flux<DataBuffer> 流式写出
import reactor.core.publisher.Mono;

import java.net.URI; // 日志中记录匹配到的后端服务 URI（如 lb://player-service）
import java.util.concurrent.ThreadLocalRandom; // 按 sampleRate 概率采样，降低高 QPS 下日志量
import java.util.concurrent.atomic.AtomicLong; // 跨线程安全累加响应字节数

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR; // exchange 中存储 Route 对象的属性键


@Component
@SuppressWarnings("null")
public class HttpResponseObserveGlobalFilter implements GlobalFilter, Ordered {

    /** 本类专用 Logger，日志前缀 GW_HTTP 便于 grep 过滤网关 HTTP 观测记录 */
    private static final Logger log = LoggerFactory.getLogger(HttpResponseObserveGlobalFilter.class);

    /** 与 TraceIdGlobalFilter 一致，从请求头读取 traceId 写入观测日志 */
    private static final String TRACE_HEADER = "X-Trace-Id";

    /** 绑定 game.gateway.observe.*：总开关、HTTP 开关、采样率、日志级别 */
    private final GatewayObserveProperties props;

    public HttpResponseObserveGlobalFilter(GatewayObserveProperties props) {
        this.props = props;
    }

    /**
     * 启用时包装 ServerHttpResponse，在 writeWith/writeAndFlushWith 中累计字节数，请求结束时打 GW_HTTP 日志。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 总开关关闭、HTTP 观测关闭、或未命中采样时，不装饰响应，零开销透传
        if (!props.isEnabled() || !props.isHttpEnabled() || !sampled(props.getSampleRate())) {
            return chain.filter(exchange);
        }

        long startNs = System.nanoTime(); // 纳秒级起点，结束时换算 latencyMs
        AtomicLong bytes = new AtomicLong(); // 响应写出过程中各 DataBuffer 可读字节累加

        ServerHttpResponse original = exchange.getResponse();
        // 匿名装饰类：delegate 到 original，仅在写出 body 时插入字节统计
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {

            /**
             * 拦截一次性写出完整 body 的路径（多数 REST 响应走此方法）。
             */
            @Override
            @NonNull
            public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux<? extends DataBuffer> flux) {
                    // 每个 DataBuffer 写出前累加 readableByteCount，不修改实际 payload
                    Flux<? extends DataBuffer> counted = flux.doOnNext(buf -> bytes.addAndGet(buf.readableByteCount()));
                    return super.writeWith(counted);
                }
                // 非 Flux 的 Publisher（少见）直接委托，不统计字节
                return super.writeWith(body);
            }

            /**
             * 拦截分块 flush 写出路径（Streaming/SSE 等场景）。
             */
            @Override
            @NonNull
            public Mono<Void> writeAndFlushWith(@NonNull Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                // 外层 Publisher 的每个 inner Flux 同样 doOnNext 累加字节
                Flux<? extends Publisher<? extends DataBuffer>> flux = Flux.from(body)
                        .map(inner -> Flux.from(inner).doOnNext(buf -> bytes.addAndGet(buf.readableByteCount())));
                return super.writeAndFlushWith(flux);
            }
        };

        ServerWebExchange newExchange = exchange.mutate().response(decorated).build();
        return chain.filter(newExchange)
                // 请求完成（成功/异常/取消）后记录观测日志，bytes.get() 为累计响应体大小
                .doFinally(st -> logResult(exchange, startNs, bytes.get()));
    }

    /**
     * 组装并输出 GW_HTTP 日志行：traceId、method、path、routeId、status、latencyMs、bytes 等。
     */
    private void logResult(ServerWebExchange exchange, long startNs, long bytes) {
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000; // 纳秒转毫秒
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "UNKNOWN";
        String path = exchange.getRequest().getURI().getPath();

        // 从 Gateway 上下文取匹配到的 Route，用于日志中标识转发目标
        Object route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        String routeId = null;
        URI routeUri = null;
        if (route instanceof org.springframework.cloud.gateway.route.Route r) {
            routeId = r.getId();       // 如 player-service-route
            routeUri = r.getUri();     // 如 lb://player-service
        }

        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int code = status != null ? status.value() : 0; // 尚未设置状态时记 0

        boolean debug = "DEBUG".equalsIgnoreCase(props.getLogLevel());
        if (debug) {
            // DEBUG 级别额外输出 routeUri，便于排查负载均衡目标
            log.debug("GW_HTTP traceId={} method={} path={} routeId={} uri={} status={} latencyMs={} bytes={}",
                    traceId, method, path, routeId, routeUri, code, latencyMs, bytes);
        } else {
            // INFO 为默认生产级别，省略 uri 减少日志体积
            log.info("GW_HTTP traceId={} method={} path={} routeId={} status={} latencyMs={} bytes={}",
                    traceId, method, path, routeId, code, latencyMs, bytes);
        }
    }

    /**
     * 按配置采样率决定是否记录本条请求：1.0 全量，0.0 全不采，中间值随机采样。
     *
     * @param rate game.gateway.observe.sample-rate，默认 1.0
     */
    private static boolean sampled(double rate) {
        if (rate >= 1.0d) {
            return true; // 100% 采样
        }
        if (rate <= 0.0d) {
            return false; // 完全关闭采样（配合 enabled 使用）
        }
        // 例如 rate=0.1 时约 10% 请求会打观测日志
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    /** order=-60：在 TraceId(-100)、Auth(-90)、RateLimit(-80) 之后执行 */
    @Override
    public int getOrder() {
        return -60;
    }
}
