/**
 * 文件说明：HTTP 链路追踪过滤器单元测试。
 * 职责：验证 TraceIdFilter 对 MDC 与响应头的 traceId 注入行为。
 */
package cn.itcast.demo.mymmorpg.web;

import jakarta.servlet.FilterChain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TraceIdFilterTest {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilterTest.class);

    @AfterMethod
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void doFilterInternal_usesHeaderTraceId() throws Exception {
        String traceId = "trace-header-abc123";
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-Trace-Id")).thenReturn(traceId);
        TraceIdFilter filter = new TraceIdFilter();
        log.info("[测试开始] 场景=透传请求头traceId | headerTraceId={}", traceId);

        filter.doFilterInternal(request, response, chain);

        log.info("[测试断言] 场景=透传请求头traceId | mdcTraceId={} | 期望={}", MDC.get(TraceIdFilter.TRACE_ID), traceId);
        verify(response).setHeader(eq("X-Trace-Id"), eq(traceId));
        verify(chain).doFilter(request, response);
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    public void doFilterInternal_generatesTraceIdWhenMissing() throws Exception {
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-Trace-Id")).thenReturn(null);
        TraceIdFilter filter = new TraceIdFilter();
        log.info("[测试开始] 场景=自动生成traceId | headerTraceId=null");

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(eq("X-Trace-Id"), org.mockito.ArgumentMatchers.argThat(v ->
                v != null && !v.isBlank() && !v.contains("-")));
        log.info("[测试断言] 场景=自动生成traceId | 期望=响应头写入32位无横线UUID");
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }
}
