/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/TraceIdGlobalFilter.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：为每个请求生成或透传 X-Trace-Id，写入 MDC 供日志关联。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;


import org.slf4j.MDC; // 线程级诊断上下文，日志 pattern 中可输出 %X{traceId}
import org.springframework.cloud.gateway.filter.GatewayFilterChain; // 注入 traceId 后继续后续过滤器与路由
import org.springframework.cloud.gateway.filter.GlobalFilter; // 全路由生效的横切过滤器
import org.springframework.core.Ordered; // order=-100 保证最先执行，下游服务与观测过滤器都能读到 traceId
import org.springframework.http.server.reactive.ServerHttpRequest; // mutate 追加 X-Trace-Id 请求头
import org.springframework.stereotype.Component; // Spring 自动注册
import org.springframework.web.server.ServerWebExchange; // 请求/响应交换对象

import reactor.core.publisher.Mono; // 响应式链式 filter + doFinally 清理 MDC


import java.util.UUID; // 客户端未传 traceId 时用 UUID 生成 32 位十六进制唯一 ID


@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    /** 分布式追踪 ID 的 HTTP 头名，与下游微服务、观测日志字段保持一致 */
    private static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * 链路追踪入口：复用客户端传入的 traceId，否则生成新 ID，写入请求头与 MDC，请求结束后清理 MDC。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 若上游（浏览器/App）已带 X-Trace-Id，则整条调用链沿用同一 ID 便于端到端排查
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            // 去掉 UUID 中的连字符，得到 32 字符紧凑格式，便于日志检索与 ELK 索引
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        String finalTraceId = traceId; // lambda 中使用的变量需 effectively final
        // 将 traceId 写入转发给后端服务的请求头，微服务日志可打印相同 traceId
        ServerHttpRequest request = exchange.getRequest().mutate().header(TRACE_HEADER, finalTraceId).build();
        ServerWebExchange newExchange = exchange.mutate().request(request).build();
        // 当前 Reactor 线程的 MDC 写入 traceId，本过滤器及同步日志可关联
        MDC.put("traceId", finalTraceId);
        return chain.filter(newExchange)
                // 无论成功、失败或取消，请求结束时移除 MDC，防止线程池复用时 traceId 串线
                .doFinally(signalType -> MDC.remove("traceId"));
    }

    /** order=-100：全过滤器链中优先级最高，认证/限流/观测均依赖此 traceId */
    @Override
    public int getOrder() {
        return -100;
    }
}
