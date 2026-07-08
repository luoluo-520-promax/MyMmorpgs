/**
 * HTTP 请求链路追踪过滤器：为每个进入 Spring MVC 的请求分配或透传 traceId，
 * 写入 MDC 供日志自动携带，并通过响应头 X-Trace-Id 返回给客户端/网关。
 */
package cn.itcast.demo.mymmorpg.web;

import jakarta.servlet.FilterChain; // Servlet 过滤器链，继续执行后续 Filter 与 Controller

import jakarta.servlet.ServletException; // Filter 链执行可能抛出的 Servlet 异常

import jakarta.servlet.http.HttpServletRequest; // 读取请求头 X-Trace-Id

import jakarta.servlet.http.HttpServletResponse; // 向响应头写回 traceId

import org.slf4j.MDC; // Mapped Diagnostic Context，线程级键值，日志 pattern 可输出 %X{traceId}

import org.springframework.lang.NonNull; // 标记参数非空，消除静态分析警告

import org.springframework.stereotype.Component; // 注册为 Spring Bean，自动加入 Servlet 过滤器链

import org.springframework.web.filter.OncePerRequestFilter; // 保证同一请求只执行一次，避免 forward/include 重复注入

import java.io.IOException; // IO 异常声明

import java.util.UUID; // 本机生成唯一 traceId

/**
 * 在 Controller 执行前注入 traceId，请求结束后清理 MDC，防止线程池复用导致 traceId 串线。
 */
@Component // Spring Boot 自动注册此 Filter
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 中 traceId 的键名，与 logback pattern %X{traceId} 对应 */
    public static final String TRACE_ID = "traceId";
    /** HTTP 请求/响应头名，与网关、OpenTelemetry 等常见约定一致 */
    private static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER); // 优先沿用上游（网关/客户端）传入的 traceId，实现跨服务串联
        if (traceId == null || traceId.isBlank()) { // 无上游 traceId 时本机生成
            traceId = UUID.randomUUID().toString().replace("-", ""); // 32 位十六进制，去掉 UUID 中的横线便于日志检索
        }
        MDC.put(TRACE_ID, traceId); // 写入当前线程 MDC，本请求后续所有 log.info/warn/error 自动带上 traceId
        response.setHeader(TRACE_HEADER, traceId); // 响应头回传 traceId，客户端出错时可报给客服/运维查日志
        try { // 确保无论 Controller 成功或抛异常，finally 都能清理 MDC
            filterChain.doFilter(request, response); // 继续执行后续 Filter 与 Controller 业务逻辑
        } finally { // 请求处理完毕（含异常路径）必定执行
            MDC.remove(TRACE_ID); // 移除 MDC 键，避免 Tomcat 线程归还池后被下一个无关请求复用同一 traceId
        }
    }
}
