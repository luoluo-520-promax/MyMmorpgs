/**
 * 文件说明：TraceIdGlobalFilter 单元测试。
 * 职责：验证 TraceId 自动生成、透传已有 X-Trace-Id 及过滤器执行顺序。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TraceIdGlobalFilter 单元测试。
 */
public class TraceIdGlobalFilterTest {

    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilterTest.class);

    private final TraceIdGlobalFilter filter = new TraceIdGlobalFilter();

    @BeforeMethod
    public void setUp() {
        log.info("[测试前置] TraceIdGlobalFilter 已加载");
    }

    @AfterMethod
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void generatesTraceIdWhenMissing() {
        String path = "/p";
        String incomingTraceId = null;
        log.info("[测试开始] 场景=自动生成TraceId | path={} | incomingTraceId={}", path, incomingTraceId);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ArgumentMatchers.any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ArgumentMatchers.argThat(e -> {
            String tid = e.getRequest().getHeaders().getFirst("X-Trace-Id");
            return tid != null && !tid.isBlank();
        }));
        log.info("[测试断言] 场景=自动生成TraceId | 期望=请求头含非空X-Trace-Id");
    }

    @Test
    public void preservesIncomingTraceId() {
        String path = "/p";
        String incomingTraceId = "fixed-id";
        log.info("[测试开始] 场景=透传TraceId | path={} | incomingTraceId={}", path, incomingTraceId);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Trace-Id", incomingTraceId).build());
        when(chain.filter(ArgumentMatchers.any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ArgumentMatchers.argThat(e ->
                incomingTraceId.equals(e.getRequest().getHeaders().getFirst("X-Trace-Id"))));
        log.info("[测试断言] 场景=透传TraceId | 期望X-Trace-Id={}", incomingTraceId);
    }

    @Test
    public void orderIsTraceFilterPriority() {
        int expectedOrder = -100;
        log.info("[测试开始] 场景=过滤器顺序 | 期望order={}", expectedOrder);

        int order = filter.getOrder();

        log.info("[测试断言] 场景=过滤器顺序 | order={} | 期望={}", order, expectedOrder);
        assertThat(order).isEqualTo(expectedOrder);
    }
}
