package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P15 Gateway 战斗/移动消息优先路由测试。
 */
public class CombatPriorityRouteFilterTest {

    @Test
    public void rewritesCombatMsgIdToSceneFastPath() {
        CombatPriorityRouteFilter filter = new CombatPriorityRouteFilter(true, 1400, 2000);
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/player/ws/move")
                .header(CombatPriorityRouteFilter.MSG_ID_HEADER, "1500")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        ServerWebExchange[] forwarded = new ServerWebExchange[1];

        filter.filter(exchange, forwardedEx -> {
            forwarded[0] = forwardedEx;
            return Mono.empty();
        }).block();

        assertThat(forwarded[0]).isNotNull();
        assertThat(forwarded[0].getRequest().getPath().value())
                .isEqualTo(CombatPriorityRouteFilter.SCENE_FAST_PATH);
        assertThat(forwarded[0].getRequest().getHeaders().getFirst("X-Combat-Fast-Path"))
                .isEqualTo("true");
        assertThat(forwarded[0].getRequest().getHeaders().getFirst("X-Routed-Target"))
                .isEqualTo("scene-service");
    }

    @Test
    public void skipsWhenMsgIdOutOfRange() {
        CombatPriorityRouteFilter filter = new CombatPriorityRouteFilter(true, 1400, 2000);
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/player/ws/move")
                .header(CombatPriorityRouteFilter.MSG_ID_HEADER, "105")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertThat(exchange.getRequest().getPath().value()).isEqualTo("/player/ws/move");
    }

    @Test
    public void skipsWhenDisabled() {
        CombatPriorityRouteFilter filter = new CombatPriorityRouteFilter(false, 1400, 2000);
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/player/ws/move")
                .header(CombatPriorityRouteFilter.MSG_ID_HEADER, "1500")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertThat(exchange.getRequest().getPath().value()).isEqualTo("/player/ws/move");
    }
}
