/**
 * 文件说明：HTTP 全局异常处理器单元测试。
 * 职责：验证 GlobalExceptionHandler 对 400/500 异常的统一响应格式。
 */
package cn.itcast.demo.mymmorpg.web;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalExceptionHandlerTest {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandlerTest.class);

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeMethod
    public void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/player/login");
        log.info("[测试前置] GlobalExceptionHandler 已加载");
    }

    @AfterMethod
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void handleBadRequest_returns400WithMessage() {
        String errorMessage = "playerId 必须大于 0";
        MDC.put(TraceIdFilter.TRACE_ID, "trace-bad-request-001");
        log.info("[测试开始] 场景=参数异常 | path={} | message={} | traceId={}",
                request.getRequestURI(), errorMessage, MDC.get(TraceIdFilter.TRACE_ID));

        ResponseEntity<ApiErrorResponse> response =
                handler.handleBadRequest(new IllegalArgumentException(errorMessage), request);

        log.info("[测试断言] 场景=参数异常 | httpStatus={} | code={} | message={} | traceId={}",
                response.getStatusCode(), response.getBody().getCode(),
                response.getBody().getMessage(), response.getBody().getTraceId());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
        assertThat(response.getBody().getTraceId()).isEqualTo("trace-bad-request-001");
        assertThat(response.getBody().getPath()).isEqualTo("/api/player/login");
    }

    @Test
    public void handleUnexpected_returns500WithGenericMessage() {
        log.info("[测试开始] 场景=未处理异常 | path={}", request.getRequestURI());

        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("db connection failed"), request);

        log.info("[测试断言] 场景=未处理异常 | httpStatus={} | code={} | message={}",
                response.getStatusCode(), response.getBody().getCode(), response.getBody().getMessage());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("服务器内部错误");
    }
}
