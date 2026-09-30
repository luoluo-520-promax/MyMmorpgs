package cn.itcast.demo.mymmorpg.gateway;

import cn.itcast.demo.mymmorpg.gateway.geo.GeoIpRegionResolver;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Locale;

/**
 * 全球就近接入：优先客户端区域头 / CF-IPCountry / 经纬度，否则按客户端 IP 做 GeoIP 粗解析，
 * 注入下游 X-Region / X-Edge-Pop / X-Redis-Shard。
 */
@Component
public class RegionAwareRoutingFilter implements GlobalFilter, Ordered {

    public static final String HEADER_REGION = "X-Region";
    public static final String HEADER_EDGE_POP = "X-Edge-Pop";
    public static final String HEADER_REDIS_SHARD = "X-Redis-Shard";
    public static final String HEADER_CLIENT_LAT = "X-Client-Lat";
    public static final String HEADER_CLIENT_LON = "X-Client-Lon";

    private final GatewayRegionProperties properties;

    public RegionAwareRoutingFilter(GatewayRegionProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest req = exchange.getRequest();
        GeoIpRegionResolver.RegionNode node = resolveNode(req);

        ServerHttpRequest mutated = req.mutate()
                .header(HEADER_REGION, node.region())
                .header(HEADER_EDGE_POP, node.edgePop())
                .header(HEADER_REDIS_SHARD, node.redisShard())
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    public GeoIpRegionResolver.RegionNode resolveNode(ServerHttpRequest req) {
        String latH = req.getHeaders().getFirst(HEADER_CLIENT_LAT);
        String lonH = req.getHeaders().getFirst(HEADER_CLIENT_LON);
        if (latH != null && lonH != null) {
            try {
                return GeoIpRegionResolver.nearestByLatLon(Double.parseDouble(latH), Double.parseDouble(lonH));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        String explicit = firstNonBlank(req.getHeaders().getFirst(HEADER_REGION),
                req.getHeaders().getFirst("CF-IPCountry"));
        if (explicit != null && !explicit.isBlank()) {
            String v = explicit.trim().toLowerCase(Locale.ROOT);
            // 已是区域码
            if (v.equals("cn-east") || v.equals("cn-north") || v.equals("ap-se")
                    || v.equals("eu-west") || v.equals("us-west") || v.equals("us-east")) {
                return GeoIpRegionResolver.resolveByCountry(normalizeCountryHint(v));
            }
            if (v.length() <= 3) {
                return GeoIpRegionResolver.resolveByCountry(v);
            }
            return GeoIpRegionResolver.resolveByCountry(normalizeCountryHint(v));
        }
        String ip = clientIp(req);
        if (ip != null) {
            return GeoIpRegionResolver.resolveByIp(ip);
        }
        return GeoIpRegionResolver.resolveByCountry(properties.getDefaultRegion());
    }

    /** 兼容旧测试与外部调用：国家码 / 区域码归一化。 */
    public static String normalizeRegion(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cn-east";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("cn-east") || v.equals("cn-north") || v.equals("ap-se")
                || v.equals("eu-west") || v.equals("us-west") || v.equals("us-east")) {
            return v;
        }
        return GeoIpRegionResolver.resolveByCountry(v).region();
    }

    private static String normalizeCountryHint(String regionOrCountry) {
        return switch (regionOrCountry) {
            case "cn-east", "cn-north" -> "cn";
            case "ap-se" -> "sg";
            case "eu-west" -> "de";
            case "us-west", "us-east" -> "us";
            default -> regionOrCountry;
        };
    }

    private static String clientIp(ServerHttpRequest req) {
        String xff = req.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remote = req.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
