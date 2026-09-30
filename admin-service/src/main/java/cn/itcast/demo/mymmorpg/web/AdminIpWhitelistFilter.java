package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.config.AdminHttpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Order(1)
@ConditionalOnProperty(name = "spring.application.name", havingValue = "admin-service")
@EnableConfigurationProperties(AdminHttpProperties.class)
public class AdminIpWhitelistFilter extends OncePerRequestFilter {

    private final AdminHttpProperties properties;

    public AdminIpWhitelistFilter(AdminHttpProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isIpWhitelistEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        List<String> allowed = properties.getIps();
        if (allowed == null || allowed.isEmpty() || !allowed.contains(clientIp)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":403,\"message\":\"IP 不在后台白名单: " + clientIp + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
