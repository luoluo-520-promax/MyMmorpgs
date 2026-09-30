/**
 * Gateway 版本门控：识别预下载预热、透传协议 Hash、拦截硬性不兼容客户端。
 */
package cn.itcast.demo.mymmorpg.gateway;

import cn.itcast.demo.mymmorpg.protocol.RetCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@SuppressWarnings("null")
public class VersionGateKeeperFilter implements GlobalFilter, Ordered {

    private static final String HEADER_CLIENT_VERSION = "X-Client-Version";
    private static final String HEADER_PROTOCOL_HASH = "X-Protocol-Schema-Hash";
    private static final String HEADER_PREHEAT = "X-Version-Preheat";

    private final GatewayVersionGateKeeper versionGateKeeper;
    private final ObjectMapper objectMapper;

    public VersionGateKeeperFilter(GatewayVersionGateKeeper versionGateKeeper, ObjectMapper objectMapper) {
        this.versionGateKeeper = versionGateKeeper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long clientVersion = parseLong(request.getHeaders().getFirst(HEADER_CLIENT_VERSION));
        String protocolHash = request.getHeaders().getFirst(HEADER_PROTOCOL_HASH);

        GatewayVersionGateKeeper.GateResult gate = versionGateKeeper.evaluateLogin(clientVersion, protocolHash);
        if (gate.hardReject()) {
            return reject(exchange, gate.retCode());
        }

        String versionCode = request.getHeaders().getFirst("X-Client-Version-Code");
        boolean preheat = versionCode != null && versionGateKeeper.isPreheatActive(versionCode);
        ServerHttpRequest mutated = request.mutate()
                .header(HEADER_PREHEAT, preheat ? "1" : "0")
                .header("X-Blocked-Msg-Ids", gate.blockedMsgIds().isEmpty() ? "" : gate.blockedMsgIds().toString())
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -90;
    }

    private Mono<Void> reject(ServerWebExchange exchange, int retCode) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of(
                    "retcode", retCode,
                    "message", retCode == RetCode.CLIENT_TOO_OLD ? "客户端版本过低，请更新" : "版本校验失败"));
        } catch (Exception e) {
            body = ("{\"retcode\":" + retCode + "}").getBytes(StandardCharsets.UTF_8);
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private static long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
