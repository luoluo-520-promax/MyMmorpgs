package cn.itcast.demo.mymmorpg.config;

import cn.itcast.demo.mymmorpg.service.AuthTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 直连 player-service 时的 HTTP 鉴权兜底（绕过网关场景）。
 * 公开路径：密钥协商；其余 HTTP 请求须携带有效 Token。
 */
@Component
public class PlayerHttpSecurityFilter extends OncePerRequestFilter implements Ordered {

    private static final String HEADER_AUTH_TOKEN = "X-Auth-Token";
    private static final List<String> PUBLIC_PATTERNS = List.of(
            "/api/security/public-key",
            "/api/security/session-key",
            "/ai/health",
            "/actuator/health-alias",
            "/actuator/health",
            "/actuator/prometheus");

    private final AuthTokenService authTokenService;
    private final boolean enabled;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PlayerHttpSecurityFilter(
            AuthTokenService authTokenService,
            @Value("${game.http-security.enabled:true}") boolean enabled) {
        this.authTokenService = authTokenService;
        this.enabled = enabled;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        if (path.startsWith("/ws") || path.startsWith("/internal/")) {
            return true;
        }
        for (String pattern : PUBLIC_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            writeUnauthorized(response, "缺少认证令牌");
            return;
        }
        Long accountId = authTokenService.getAccountIdByToken(token);
        if (accountId == null) {
            writeUnauthorized(response, "认证令牌无效或已过期");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        String headerToken = request.getHeader(HEADER_AUTH_TOKEN);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        return request.getParameter("token");
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
