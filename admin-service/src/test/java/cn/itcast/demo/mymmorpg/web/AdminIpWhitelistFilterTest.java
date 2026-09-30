package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.config.AdminHttpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Admin IP 白名单：未命中拒绝，命中放行。
 */
public class AdminIpWhitelistFilterTest {

    private AdminHttpProperties properties;
    private AdminIpWhitelistFilter filter;
    private FilterChain chain;

    @BeforeMethod
    public void setUp() {
        properties = new AdminHttpProperties();
        properties.setIpWhitelistEnabled(true);
        properties.setIps(List.of("10.0.0.1", "127.0.0.1"));
        filter = new AdminIpWhitelistFilter(properties);
        chain = mock(FilterChain.class);
    }

    @Test
    public void resolveClientIp_prefersXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.setRemoteAddr("127.0.0.1");
        assertThat(AdminIpWhitelistFilter.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    public void doFilter_ipNotAllowed_forbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/hot-reload");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("IP 不在后台白名单");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void doFilter_ipAllowed_continues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/hot-reload");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                org.mockito.ArgumentMatchers.any(HttpServletResponse.class));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void shouldNotFilter_whenDisabled() throws Exception {
        properties.setIpWhitelistEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/x");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
