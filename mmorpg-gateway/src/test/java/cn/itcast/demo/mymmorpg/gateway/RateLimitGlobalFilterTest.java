/**
 * 文件说明：RateLimitGlobalFilter 单元测试。
 * 职责：验证限流开关、白名单、全站超限 429、用户超限 429、未超限放行。
 */
package cn.itcast.demo.mymmorpg.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RateLimitGlobalFilter 单元测试。
 */
public class RateLimitGlobalFilterTest {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilterTest.class);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private GatewayFilterChain chain;

    private AutoCloseable mocks;

    private GatewayRateLimitProperties props;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RateLimitGlobalFilter filter;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        props = new GatewayRateLimitProperties();
        filter = new RateLimitGlobalFilter(redisTemplate, props, objectMapper);
        log.info("[测试前置] RateLimitGlobalFilter Mock 依赖已初始化");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void whenDisabled_delegatesToChain() {
        boolean enabled = false;
        String path = "/a";
        log.info("[测试开始] 场景=限流关闭透传 | enabled={} | path={}", enabled, path);

        props.setEnabled(enabled);
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        log.info("[测试断言] 场景=限流关闭透传 | chainCalled={} | 期望=透传", true);
    }

    @Test
    public void whenPathWhitelisted_delegatesToChain() {
        boolean enabled = true;
        String whitelistPattern = "/actuator/**";
        String path = "/actuator/health";
        log.info("[测试开始] 场景=白名单放行 | enabled={} | whitelist={} | path={}",
                enabled, whitelistPattern, path);

        props.setEnabled(enabled);
        props.setWhitelist(List.of(whitelistPattern));
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        verify(valueOps, never()).increment(anyString());
        log.info("[测试断言] 场景=白名单放行 | chainCalled={} | redisIncr={} | 期望=放行且不计数",
                true, false);
    }

    @Test
    public void whenTotalLimitExceeded_returns429() {
        boolean enabled = true;
        int totalPerSecond = 2;
        int userPerSecond = 0;
        long incrementResult = 3L;
        String path = "/r";
        log.info("[测试开始] 场景=全站超限 | enabled={} | totalPerSecond={} | userPerSecond={} | incrementResult={} | path={}",
                enabled, totalPerSecond, userPerSecond, incrementResult, path);

        props.setEnabled(enabled);
        props.setWhitelist(List.of());
        props.setTotalPerSecond(totalPerSecond);
        props.setUserPerSecond(userPerSecond);
        when(valueOps.increment(anyString())).thenReturn(incrementResult);

        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        int statusCode = ex.getResponse().getStatusCode().value();
        log.info("[测试断言] 场景=全站超限 | status={} | 期望={}", statusCode, HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    public void whenUserLimitExceeded_returns429() {
        boolean enabled = true;
        int totalPerSecond = 100;
        int userPerSecond = 2;
        String accountId = "10001";
        long totalIncrement = 1L;
        long userIncrement = 3L;
        String path = "/api/item";
        log.info("[测试开始] 场景=用户超限 | enabled={} | totalPerSecond={} | userPerSecond={} | accountId={} | userIncrement={} | path={}",
                enabled, totalPerSecond, userPerSecond, accountId, userIncrement, path);

        props.setEnabled(enabled);
        props.setWhitelist(List.of());
        props.setTotalPerSecond(totalPerSecond);
        props.setUserPerSecond(userPerSecond);
        when(valueOps.increment(anyString())).thenReturn(totalIncrement, userIncrement);

        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Account-Id", accountId).build());
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        int statusCode = ex.getResponse().getStatusCode().value();
        log.info("[测试断言] 场景=用户超限 | status={} | 期望={}", statusCode, HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    public void whenUnderLimit_delegatesToChain() {
        boolean enabled = true;
        int totalPerSecond = 100;
        int userPerSecond = 0;
        long incrementResult = 1L;
        String path = "/ok";
        log.info("[测试开始] 场景=未超限放行 | enabled={} | totalPerSecond={} | incrementResult={} | path={}",
                enabled, totalPerSecond, incrementResult, path);

        props.setEnabled(enabled);
        props.setWhitelist(List.of());
        props.setTotalPerSecond(totalPerSecond);
        props.setUserPerSecond(userPerSecond);
        when(valueOps.increment(anyString())).thenReturn(incrementResult);

        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        when(chain.filter(ex)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        verify(chain).filter(ex);
        log.info("[测试断言] 场景=未超限放行 | chainCalled={} | 期望=透传", true);
    }

    @Test
    public void orderIsRateLimitFilterPriority() {
        int expectedOrder = -80;
        log.info("[测试开始] 场景=过滤器顺序 | 期望order={}", expectedOrder);

        int order = filter.getOrder();

        log.info("[测试断言] 场景=过滤器顺序 | order={} | 期望={}", order, expectedOrder);
        assertThat(order).isEqualTo(expectedOrder);
    }
}
