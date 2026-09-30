package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 战斗/移动消息 L4 优先路由：X-Msg-Id 在 1400~2000 号段时直转 scene-service，
 * 绕过 player-service DispatcherServlet 线程池，减少队头阻塞。
 */
@Component
public class CombatPriorityRouteFilter implements GlobalFilter, Ordered {

    public static final String MSG_ID_HEADER = "X-Msg-Id";
    public static final int COMBAT_MSG_MIN = 1400;
    public static final int COMBAT_MSG_MAX = 2000;
    public static final String SCENE_FAST_PATH = "/internal/scene/fast-path";

    private final int combatMsgMin;
    private final int combatMsgMax;
    private final boolean enabled;

    public CombatPriorityRouteFilter(
            @Value("${game.gateway.combat-route.enabled:true}") boolean enabled,
            @Value("${game.gateway.combat-route.msg-min:1400}") int combatMsgMin,
            @Value("${game.gateway.combat-route.msg-max:2000}") int combatMsgMax) {
        this.enabled = enabled;
        this.combatMsgMin = combatMsgMin;
        this.combatMsgMax = combatMsgMax;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String msgIdRaw = request.getHeaders().getFirst(MSG_ID_HEADER);
        if (msgIdRaw == null || msgIdRaw.isBlank()) {
            return chain.filter(exchange);
        }
        int msgId;
        try {
            msgId = Integer.parseInt(msgIdRaw.trim());
        } catch (NumberFormatException e) {
            return chain.filter(exchange);
        }
        if (msgId < combatMsgMin || msgId > combatMsgMax) {
            return chain.filter(exchange);
        }
        ServerHttpRequest rewritten = request.mutate()
                .header("X-Combat-Fast-Path", "true")
                .header("X-Routed-Target", "scene-service")
                .path(SCENE_FAST_PATH)
                .build();
        return chain.filter(exchange.mutate().request(rewritten).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
