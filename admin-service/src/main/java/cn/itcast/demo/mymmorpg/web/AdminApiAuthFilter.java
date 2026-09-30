package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.config.AdminHttpProperties;
import cn.itcast.demo.mymmorpg.security.AdminApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.AdminApiSignUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 校验 /admin/** 请求的 HMAC 签名或 API Key，并将 adminUserId 写入请求属性。
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@EnableConfigurationProperties(AdminHttpProperties.class)
public class AdminApiAuthFilter extends OncePerRequestFilter {

    private final AdminHttpProperties properties;

    public AdminApiAuthFilter(AdminHttpProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/admin/")) {
            return true;
        }
        return path.startsWith("/admin/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
        byte[] body = wrapped.getCachedBody();

        Long adminUserId = resolveAdminUserId(wrapped);
        if (adminUserId == null) {
            writeUnauthorized(response, "缺少或无效的后台用户 ID");
            return;
        }

        if (properties.isAuthEnabled()) {
            if (!authenticate(wrapped, body, adminUserId)) {
                writeUnauthorized(response, "后台 API 鉴权失败");
                return;
            }
        }

        wrapped.setAttribute(AdminRequestAttributes.ADMIN_USER_ID, adminUserId);
        filterChain.doFilter(wrapped, response);
    }

    private boolean authenticate(HttpServletRequest request, byte[] body, long adminUserId) {
        String apiKeyHeader = request.getHeader(AdminApiAuthHeaders.API_KEY);
        if (StringUtils.hasText(properties.getApiKey())
                && properties.getApiKey().equals(apiKeyHeader)) {
            return true;
        }

        String tsHeader = request.getHeader(AdminApiAuthHeaders.TIMESTAMP);
        String signature = request.getHeader(AdminApiAuthHeaders.SIGNATURE);
        if (tsHeader == null || signature == null) {
            return false;
        }
        try {
            long timestamp = Long.parseLong(tsHeader);
            return AdminApiSignUtil.verify(
                    properties.getHmacSecret(),
                    timestamp,
                    request.getMethod(),
                    request.getRequestURI(),
                    adminUserId,
                    body,
                    signature,
                    System.currentTimeMillis());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Long resolveAdminUserId(HttpServletRequest request) {
        String raw = request.getHeader(AdminApiAuthHeaders.USER_ID);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
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
