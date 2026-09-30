package cn.itcast.demo.mymmorpg.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 无缝跨区路由：根据坐标头查路由表，注入目标 scene 节点头，配合 Player Proxy 握手实现无感知迁移。
 */
@Component
public class SeamlessZoneRoutingFilter implements GlobalFilter, Ordered {

    public static final String HEADER_WORLD_ID = "X-World-Id";
    public static final String HEADER_POS_X = "X-Pos-X";
    public static final String HEADER_POS_Z = "X-Pos-Z";
    public static final String HEADER_SCENE_NODE = "X-Scene-Node";
    public static final String HEADER_SCENE_HOST = "X-Scene-Host";
    public static final String HEADER_SCENE_PORT = "X-Scene-Port";
    public static final String HEADER_ZONE_ID = "X-Zone-Id";
    public static final String HEADER_CURRENT_NODE = "X-Current-Scene-Node";

    private final GatewayZoneRouteTable routingTable;

    public SeamlessZoneRoutingFilter(GatewayZoneRouteTable routingTable) {
        this.routingTable = routingTable;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String worldRaw = req.getHeaders().getFirst(HEADER_WORLD_ID);
        String xRaw = req.getHeaders().getFirst(HEADER_POS_X);
        String zRaw = req.getHeaders().getFirst(HEADER_POS_Z);
        if (worldRaw == null || xRaw == null || zRaw == null) {
            return chain.filter(exchange);
        }
        int worldId;
        float x;
        float z;
        try {
            worldId = Integer.parseInt(worldRaw.trim());
            x = Float.parseFloat(xRaw.trim());
            z = Float.parseFloat(zRaw.trim());
        } catch (NumberFormatException e) {
            return chain.filter(exchange);
        }
        String current = req.getHeaders().getFirst(HEADER_CURRENT_NODE);
        GatewayZoneRouteTable.RouteLookup lookup = routingTable.lookup(worldId, x, z, 100, current);
        if (!lookup.found()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest mutated = req.mutate()
                .header(HEADER_SCENE_NODE, lookup.nodeId())
                .header(HEADER_SCENE_HOST, lookup.host())
                .header(HEADER_SCENE_PORT, String.valueOf(lookup.port()))
                .header(HEADER_ZONE_ID, String.valueOf(lookup.zoneId()))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 25;
    }
}
