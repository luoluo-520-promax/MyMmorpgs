package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.service.AuthTokenService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手鉴权：若携带 Token 则校验并写入 accountId；无 Token 时允许连接（首帧 AccountLogin 登录）。
 */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ACCOUNT_ID = "accountId";

    private final AuthTokenService authTokenService;

    public WebSocketAuthHandshakeInterceptor(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            return true;
        }
        Long accountId = authTokenService.getAccountIdByToken(token);
        if (accountId == null) {
            return false;
        }
        attributes.put(ATTR_ACCOUNT_ID, accountId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private static String resolveToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            var req = servletRequest.getServletRequest();
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return auth.substring(7).trim();
            }
            String headerToken = req.getHeader("X-Auth-Token");
            if (headerToken != null && !headerToken.isBlank()) {
                return headerToken;
            }
            return req.getParameter("token");
        }
        return null;
    }
}
