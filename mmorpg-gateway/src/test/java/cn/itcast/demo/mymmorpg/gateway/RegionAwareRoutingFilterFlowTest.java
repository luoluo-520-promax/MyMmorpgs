package cn.itcast.demo.mymmorpg.gateway;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全球就近接入：区域头注入与关闭开关。
 */
public class RegionAwareRoutingFilterFlowTest {

    @Mock
    private GatewayFilterChain chain;

    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    public void injectsRegionAndEdgePopFromHeader() {
        GatewayRegionProperties props = new GatewayRegionProperties();
        props.setEnabled(true);
        props.setDefaultRegion("cn-east");
        props.setDefaultEdgePop("pop-shanghai");
        RegionAwareRoutingFilter filter = new RegionAwareRoutingFilter(props);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/player/x")
                        .header(RegionAwareRoutingFilter.HEADER_REGION, "us-west")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange forwarded = captor.getValue();
        assertThat(forwarded.getRequest().getHeaders().getFirst(RegionAwareRoutingFilter.HEADER_REGION))
                .isEqualTo("us-west");
        assertThat(forwarded.getRequest().getHeaders().getFirst(RegionAwareRoutingFilter.HEADER_EDGE_POP))
                .isEqualTo("pop-oregon");
    }

    @Test
    public void mapsCountryCodeWhenRegionMissing() {
        GatewayRegionProperties props = new GatewayRegionProperties();
        props.setEnabled(true);
        RegionAwareRoutingFilter filter = new RegionAwareRoutingFilter(props);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/player/x")
                        .header("CF-IPCountry", "SG")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertThat(captor.getValue().getRequest().getHeaders()
                .getFirst(RegionAwareRoutingFilter.HEADER_REGION)).isEqualTo("ap-se");
        assertThat(captor.getValue().getRequest().getHeaders()
                .getFirst(RegionAwareRoutingFilter.HEADER_EDGE_POP)).isEqualTo("pop-singapore");
    }

    @Test
    public void disabled_skipsMutation() {
        GatewayRegionProperties props = new GatewayRegionProperties();
        props.setEnabled(false);
        RegionAwareRoutingFilter filter = new RegionAwareRoutingFilter(props);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/player/x").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(exchange);
    }
}
