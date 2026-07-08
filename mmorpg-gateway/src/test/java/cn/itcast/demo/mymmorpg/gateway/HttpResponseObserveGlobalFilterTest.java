/**
 * 文件说明：HttpResponseObserveGlobalFilter 单元测试。
 * 职责：验证 HTTP 观测开关、采样率、响应装饰与过滤器顺序。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HttpResponseObserveGlobalFilter 单元测试。
 */
public class HttpResponseObserveGlobalFilterTest {

    private static final Logger log = LoggerFactory.getLogger(HttpResponseObserveGlobalFilterTest.class);

    private GatewayObserveProperties props;

    private HttpResponseObserveGlobalFilter filter;

    @BeforeMethod
    public void setUp() {
        props = new GatewayObserveProperties();
        filter = new HttpResponseObserveGlobalFilter(props);
        log.info("[测试前置] HttpResponseObserveGlobalFilter 已加载");
    }

    @Test
    public void whenObserveDisabled_delegatesToChain() {
        boolean enabled = false;
        String path = "/api/player";
        log.info("[测试开始] 场景=观测总开关关闭 | enabled={} | path={}", enabled, path);

        props.setEnabled(enabled);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        log.info("[测试断言] 场景=观测总开关关闭 | chainCalled={} | 期望=原exchange透传", true);
    }

    @Test
    public void whenHttpObserveDisabled_delegatesToChain() {
        boolean enabled = true;
        boolean httpEnabled = false;
        String path = "/api/player";
        log.info("[测试开始] 场景=HTTP观测关闭 | enabled={} | httpEnabled={} | path={}",
                enabled, httpEnabled, path);

        props.setEnabled(enabled);
        props.setHttpEnabled(httpEnabled);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        log.info("[测试断言] 场景=HTTP观测关闭 | chainCalled={} | 期望=原exchange透传", true);
    }

    @Test
    public void whenSampleRateZero_delegatesToChain() {
        boolean enabled = true;
        boolean httpEnabled = true;
        double sampleRate = 0.0d;
        String path = "/api/player";
        log.info("[测试开始] 场景=采样率为零 | enabled={} | httpEnabled={} | sampleRate={} | path={}",
                enabled, httpEnabled, sampleRate, path);

        props.setEnabled(enabled);
        props.setHttpEnabled(httpEnabled);
        props.setSampleRate(sampleRate);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        log.info("[测试断言] 场景=采样率为零 | chainCalled={} | 期望=不装饰响应", true);
    }

    @Test
    public void whenEnabled_decoratesResponseAndWritesBody() {
        boolean enabled = true;
        boolean httpEnabled = true;
        double sampleRate = 1.0d;
        String path = "/api/player";
        String traceId = "trace-http-001";
        String responseBody = "ok";
        log.info("[测试开始] 场景=HTTP观测启用 | enabled={} | httpEnabled={} | sampleRate={} | path={} | traceId={} | responseBody={}",
                enabled, httpEnabled, sampleRate, path, traceId, responseBody);

        props.setEnabled(enabled);
        props.setHttpEnabled(httpEnabled);
        props.setSampleRate(sampleRate);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        ServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Trace-Id", traceId).build());
        ArgumentCaptor<ServerWebExchange> cap = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(cap.capture())).thenAnswer(inv -> {
            ServerWebExchange mutated = cap.getValue();
            mutated.getResponse().setStatusCode(HttpStatus.OK);
            DataBuffer buffer = mutated.getResponse().bufferFactory().wrap(responseBody.getBytes());
            return mutated.getResponse().writeWith(Mono.just(buffer));
        });

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        int statusCode = ex.getResponse().getStatusCode().value();
        log.info("[测试断言] 场景=HTTP观测启用 | status={} | 期望={} | responseDecorated={}",
                statusCode, HttpStatus.OK.value(), cap.getValue().getResponse() != ex.getResponse());
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cap.getValue().getResponse()).isNotSameAs(ex.getResponse());
    }

    @Test
    public void orderIsHttpObserveFilterPriority() {
        int expectedOrder = -60;
        log.info("[测试开始] 场景=过滤器顺序 | 期望order={}", expectedOrder);

        int order = filter.getOrder();

        log.info("[测试断言] 场景=过滤器顺序 | order={} | 期望={}", order, expectedOrder);
        assertThat(order).isEqualTo(expectedOrder);
    }
}
