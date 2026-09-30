/**
 * 文件说明：AuthGlobalFilter 单元测试。
 * 职责：验证认证开关、白名单、Token 缺失/无效、Bearer 校验与 X-Account-Id 透传。
 */
package cn.itcast.demo.mymmorpg.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthGlobalFilter 单元测试。
 */
public class AuthGlobalFilterTest {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilterTest.class);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private GatewayFilterChain chain;

    private AutoCloseable mocks;

    private ObjectMapper objectMapper;

    @BeforeMethod
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        log.info("[测试前置] AuthGlobalFilter Mock 依赖已初始化");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void whenAuthDisabled_delegatesToChain() {
        boolean enabled = false;
        String path = "/api/x";
        log.info("[测试开始] 场景=认证关闭透传 | enabled={} | path={}", enabled, path);

        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setEnabled(enabled);
        AuthGlobalFilter filter = new AuthGlobalFilter(redisTemplate, props, objectMapper);
        ServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        verify(redisTemplate, never()).opsForValue();
        log.info("[测试断言] 场景=认证关闭透传 | chainCalled={} | redisAccessed={} | 期望=透传且不查Redis",
                true, false);
    }

    @Test
    public void whenPathWhitelisted_delegatesToChain() {
        boolean enabled = true;
        String whitelistPattern = "/public/**";
        String path = "/public/health";
        log.info("[测试开始] 场景=白名单放行 | enabled={} | whitelist={} | path={}",
                enabled, whitelistPattern, path);

        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setEnabled(enabled);
        props.setWhitelist(List.of(whitelistPattern));
        AuthGlobalFilter filter = new AuthGlobalFilter(redisTemplate, props, objectMapper);
        ServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        verify(redisTemplate, never()).opsForValue();
        log.info("[测试断言] 场景=白名单放行 | chainCalled={} | redisAccessed={} | 期望=放行且不查Redis",
                true, false);
    }

    @Test
    public void whenNoToken_returnsUnauthorized() {
        boolean enabled = true;
        String path = "/secure/data";
        log.info("[测试开始] 场景=缺少Token | enabled={} | path={} | token=无", enabled, path);

        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setEnabled(enabled);
        props.setWhitelist(List.of());
        AuthGlobalFilter filter = new AuthGlobalFilter(redisTemplate, props, objectMapper);
        ServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain, never()).filter(ex);
        int statusCode = ex.getResponse().getStatusCode().value();
        log.info("[测试断言] 场景=缺少Token | status={} | 期望={}", statusCode, HttpStatus.UNAUTHORIZED.value());
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void whenInvalidToken_returnsUnauthorized() {
        String token = "expired-tok";
        String redisKey = "auth:token:" + token;
        String path = "/api/p";
        log.info("[测试开始] 场景=Token无效 | path={} | token={} | redisKey={} | redisValue=null",
                path, token, redisKey);

        when(valueOps.get(redisKey)).thenReturn(null);
        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setEnabled(true);
        props.setWhitelist(List.of());
        AuthGlobalFilter filter = new AuthGlobalFilter(redisTemplate, props, objectMapper);
        ServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain, never()).filter(ex);
        int statusCode = ex.getResponse().getStatusCode().value();
        log.info("[测试断言] 场景=Token无效 | status={} | 期望={}", statusCode, HttpStatus.UNAUTHORIZED.value());
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void whenBearerValid_propagatesAccountIdHeader() {
        String token = "tok1";
        String accountId = "42";
        String redisKey = "auth:token:" + token;
        String path = "/api/p";
        log.info("[测试开始] 场景=Bearer有效 | path={} | token={} | redisKey={} | accountId={}",
                path, token, redisKey, accountId);

        when(valueOps.get(redisKey)).thenReturn(accountId);
        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setEnabled(true);
        props.setWhitelist(List.of());
        AuthGlobalFilter filter = new AuthGlobalFilter(redisTemplate, props, objectMapper);
        ServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
        ArgumentCaptor<ServerWebExchange> cap = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(cap.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        String headerAccountId = cap.getValue().getRequest().getHeaders().getFirst("X-Account-Id");
        log.info("[测试断言] 场景=Bearer有效 | X-Account-Id={} | 期望={}", headerAccountId, accountId);
        verify(chain).filter(any(ServerWebExchange.class));
        assertThat(headerAccountId).isEqualTo(accountId);
    }

    @Test
    public void whenWebSocketUpgradeWithoutToken_delegatesToChain() {
        GatewayAuthProperties props = new GatewayAuthProperties();
        props.setEnabled(true);
        props.setWhitelist(List.of());
        AuthGlobalFilter filter = new AuthGlobalFilter(redisTemplate, props, objectMapper);
        ServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws/player").header("Sec-WebSocket-Key", "abc").build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    public void orderIsAuthFilterPriority() {
        int expectedOrder = -90;
        log.info("[测试开始] 场景=过滤器顺序 | 期望order={}", expectedOrder);

        GatewayAuthProperties props = new GatewayAuthProperties();
        AuthGlobalFilter filter = new AuthGlobalFilter(redisTemplate, props, objectMapper);
        int order = filter.getOrder();

        log.info("[测试断言] 场景=过滤器顺序 | order={} | 期望={}", order, expectedOrder);
        assertThat(order).isEqualTo(expectedOrder);
    }
}
