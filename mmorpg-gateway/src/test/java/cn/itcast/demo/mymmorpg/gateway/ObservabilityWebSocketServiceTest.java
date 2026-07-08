/**
 * 文件说明：ObservabilityWebSocketService 单元测试。
 * 职责：验证 WS 观测开关、采样率、会话装饰与委托行为。
 */
package cn.itcast.demo.mymmorpg.gateway;

import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ObservabilityWebSocketService 单元测试。
 */
public class ObservabilityWebSocketServiceTest {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityWebSocketServiceTest.class);

    private WebSocketService delegate;

    private GatewayObserveProperties props;

    private ObservabilityWebSocketService service;

    @BeforeMethod
    public void setUp() {
        delegate = mock(WebSocketService.class);
        props = new GatewayObserveProperties();
        service = new ObservabilityWebSocketService(delegate, props);
        log.info("[测试前置] ObservabilityWebSocketService Mock 依赖已初始化");
    }

    @Test
    public void whenObserveDisabled_delegatesToDelegate() {
        boolean enabled = false;
        String path = "/ws";
        log.info("[测试开始] 场景=观测总开关关闭 | enabled={} | path={}", enabled, path);

        props.setEnabled(enabled);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        WebSocketHandler handler = session -> Mono.empty();
        when(delegate.handleRequest(exchange, handler)).thenReturn(Mono.empty());

        StepVerifier.create(service.handleRequest(exchange, handler)).verifyComplete();

        verify(delegate).handleRequest(exchange, handler);
        log.info("[测试断言] 场景=观测总开关关闭 | delegateCalled={} | 期望=直接委托原handler", true);
    }

    @Test
    public void whenWsObserveDisabled_delegatesToDelegate() {
        boolean enabled = true;
        boolean wsEnabled = false;
        String path = "/ws";
        log.info("[测试开始] 场景=WS观测关闭 | enabled={} | wsEnabled={} | path={}",
                enabled, wsEnabled, path);

        props.setEnabled(enabled);
        props.setWsEnabled(wsEnabled);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        WebSocketHandler handler = session -> Mono.empty();
        when(delegate.handleRequest(exchange, handler)).thenReturn(Mono.empty());

        StepVerifier.create(service.handleRequest(exchange, handler)).verifyComplete();

        verify(delegate).handleRequest(exchange, handler);
        log.info("[测试断言] 场景=WS观测关闭 | delegateCalled={} | 期望=直接委托原handler", true);
    }

    @Test
    public void whenSampleRateZero_delegatesToDelegate() {
        boolean enabled = true;
        boolean wsEnabled = true;
        double sampleRate = 0.0d;
        String path = "/ws";
        log.info("[测试开始] 场景=采样率为零 | enabled={} | wsEnabled={} | sampleRate={} | path={}",
                enabled, wsEnabled, sampleRate, path);

        props.setEnabled(enabled);
        props.setWsEnabled(wsEnabled);
        props.setSampleRate(sampleRate);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        WebSocketHandler handler = session -> Mono.empty();
        when(delegate.handleRequest(exchange, handler)).thenReturn(Mono.empty());

        StepVerifier.create(service.handleRequest(exchange, handler)).verifyComplete();

        verify(delegate).handleRequest(exchange, handler);
        log.info("[测试断言] 场景=采样率为零 | delegateCalled={} | 期望=不包装会话", true);
    }

    @Test
    public void whenEnabled_wrapsHandlerAndObservesMessages() {
        boolean enabled = true;
        boolean wsEnabled = true;
        double sampleRate = 1.0d;
        String path = "/ws";
        String traceId = "trace-ws-001";
        String inboundText = "hello-ws";
        log.info("[测试开始] 场景=WS观测启用 | enabled={} | wsEnabled={} | sampleRate={} | path={} | traceId={} | inboundText={}",
                enabled, wsEnabled, sampleRate, path, traceId, inboundText);

        props.setEnabled(enabled);
        props.setWsEnabled(wsEnabled);
        props.setSampleRate(sampleRate);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Trace-Id", traceId).build());
        WebSocketHandler handler = session -> Mono.empty();
        ArgumentCaptor<WebSocketHandler> handlerCaptor = ArgumentCaptor.forClass(WebSocketHandler.class);
        when(delegate.handleRequest(eq(exchange), handlerCaptor.capture())).thenAnswer(inv -> {
            WebSocketHandler wrappedHandler = handlerCaptor.getValue();
            WebSocketSession session = mock(WebSocketSession.class);
            DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
            WebSocketMessage inbound = new WebSocketMessage(WebSocketMessage.Type.TEXT,
                    bufferFactory.wrap(inboundText.getBytes()));
            when(session.receive()).thenReturn(Flux.just(inbound));
            when(session.send(any())).thenReturn(Mono.empty());
            return wrappedHandler.handle(session);
        });

        StepVerifier.create(service.handleRequest(exchange, handler)).verifyComplete();

        verify(delegate).handleRequest(eq(exchange), any(WebSocketHandler.class));
        verify(delegate, never()).handleRequest(exchange, handler);
        log.info("[测试断言] 场景=WS观测启用 | wrappedHandlerUsed={} | 期望=委托包装后的handler", true);
    }
}
