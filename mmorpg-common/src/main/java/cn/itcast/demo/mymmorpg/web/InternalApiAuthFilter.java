package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.config.InternalApiAuthProperties;
import cn.itcast.demo.mymmorpg.security.InternalApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.InternalApiSignUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 校验 /internal/** 请求的服务间 HMAC 签名，防止伪造 X-Player-Id。
 */
public class InternalApiAuthFilter extends OncePerRequestFilter {

    private final InternalApiAuthProperties properties;

    public InternalApiAuthFilter(InternalApiAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
        byte[] body = wrapped.getCachedBody();

        String tsHeader = wrapped.getHeader(InternalApiAuthHeaders.TIMESTAMP);
        String signature = wrapped.getHeader(InternalApiAuthHeaders.SIGNATURE);
        String playerHeader = wrapped.getHeader(InternalApiAuthHeaders.PLAYER_ID);

        if (tsHeader == null || signature == null || playerHeader == null) {
            writeUnauthorized(response, "缺少内部 API 鉴权头");
            return;
        }

        long timestamp;
        long playerId;
        try {
            timestamp = Long.parseLong(tsHeader);
            playerId = Long.parseLong(playerHeader);
        } catch (NumberFormatException e) {
            writeUnauthorized(response, "内部 API 鉴权头格式错误");
            return;
        }

        boolean ok = InternalApiSignUtil.verify(
                properties.getSecret(),
                timestamp,
                wrapped.getMethod(),
                wrapped.getRequestURI(),
                playerId,
                body,
                signature,
                System.currentTimeMillis());

        if (!ok) {
            writeUnauthorized(response, "内部 API 签名无效或已过期");
            return;
        }

        filterChain.doFilter(wrapped, response);
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String json = "{\"code\":401,\"message\":\"" + message + "\"}";
        response.getWriter().write(json);
    }

    static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            cachedBody = request.getInputStream().readAllBytes();
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
