package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 按协议功能号段注入目标服务头（轻量，不依赖 mmorpg-common）。
 * 与 {@code FunctionRouteTable} 号段约定对齐，便于后续剥离 player 中转。
 */
@Component
public class FunctionNumberRoutingFilter implements GlobalFilter, Ordered {

    public static final String HEADER_MSG_ID = "X-Msg-Id";
    public static final String HEADER_TARGET_SERVICE = "X-Target-Service";
    public static final String HEADER_ROUTE_NOTE = "X-Route-Note";

    private final NavigableMap<Integer, String> ranges = new TreeMap<>();

    public FunctionNumberRoutingFilter() {
        ranges.put(1, "PLAYER");
        ranges.put(100, "SCENE");
        ranges.put(200, "BATTLE");
        ranges.put(300, "BAG");
        ranges.put(400, "SKILL");
        ranges.put(500, "CHAT");
        ranges.put(600, "HALL");
        ranges.put(700, "QUEST");
        ranges.put(800, "ACTIVITY");
        ranges.put(900, "MATCH");
        ranges.put(1000, "SHOP");
        ranges.put(1100, "UPDATE");
        ranges.put(1400, "GACHA");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String msgRaw = req.getHeaders().getFirst(HEADER_MSG_ID);
        if (msgRaw == null || msgRaw.isBlank()) {
            return chain.filter(exchange);
        }
        int msgId;
        try {
            msgId = Integer.parseInt(msgRaw.trim());
        } catch (NumberFormatException e) {
            return chain.filter(exchange);
        }
        String target = resolve(msgId);
        ServerHttpRequest mutated = req.mutate()
                .header(HEADER_TARGET_SERVICE, target)
                .header(HEADER_ROUTE_NOTE, "function-range")
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    public String resolve(int msgId) {
        Map.Entry<Integer, String> e = ranges.floorEntry(msgId);
        return e == null ? "PLAYER" : e.getValue();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
