/**
 * 文件说明：ApiErrorResponse 工厂方法单元测试。
 * 职责：验证 traceId 优先从 MDC 读取，其次回退请求头。
 */
package cn.itcast.demo.mymmorpg.web;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ApiErrorResponseTest {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorResponseTest.class);

    @AfterMethod
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void of_usesMdcTraceIdWhenPresent() {
        String traceId = "trace-from-mdc";
        int code = 400;
        String message = "bad";
        String path = "/api/x";
        MDC.put(TraceIdFilter.TRACE_ID, traceId);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(path);
        log.info("[测试开始] 场景=MDC优先traceId | code={} | message={} | path={} | mdcTraceId={}",
                code, message, path, traceId);

        ApiErrorResponse r = ApiErrorResponse.of(code, message, req);

        log.info("[测试断言] 场景=MDC优先traceId | traceId={} | path={} | code={} | message={} | 期望traceId={}",
                r.getTraceId(), r.getPath(), r.getCode(), r.getMessage(), traceId);
        assertThat(r.getTraceId()).isEqualTo(traceId);
        assertThat(r.getPath()).isEqualTo(path);
        assertThat(r.getCode()).isEqualTo(code);
        assertThat(r.getMessage()).isEqualTo(message);
    }

    @Test
    public void of_fallsBackToHeaderWhenMdcEmpty() {
        String headerTraceId = "hdr-1";
        int code = 500;
        String message = "err";
        String path = "/y";
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(path);
        when(req.getHeader("X-Trace-Id")).thenReturn(headerTraceId);
        log.info("[测试开始] 场景=回退请求头traceId | code={} | message={} | path={} | headerTraceId={}",
                code, message, path, headerTraceId);

        ApiErrorResponse r = ApiErrorResponse.of(code, message, req);

        log.info("[测试断言] 场景=回退请求头traceId | traceId={} | 期望={}", r.getTraceId(), headerTraceId);
        assertThat(r.getTraceId()).isEqualTo(headerTraceId);
    }
}
