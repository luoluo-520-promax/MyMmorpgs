package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.config.AdminHttpProperties;
import cn.itcast.demo.mymmorpg.security.AdminApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.AdminApiSignUtil;
import jakarta.servlet.FilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Admin API 鉴权：缺用户 ID / 签名失败拒绝；API Key 或 HMAC 通过。
 */
public class AdminApiAuthFilterTest {

    private AdminHttpProperties properties;
    private AdminApiAuthFilter filter;
    private FilterChain chain;

    @BeforeMethod
    public void setUp() {
        properties = new AdminHttpProperties();
        properties.setAuthEnabled(true);
        properties.setHmacSecret("test-secret");
        properties.setApiKey("ops-key");
        filter = new AdminApiAuthFilter(properties);
        chain = mock(FilterChain.class);
    }

    @Test
    public void missingUserId_unauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/complaints");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void apiKey_allows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/complaints");
        request.addHeader(AdminApiAuthHeaders.USER_ID, "7");
        request.addHeader(AdminApiAuthHeaders.API_KEY, "ops-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    public void hmac_allows() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        long ts = System.currentTimeMillis();
        String sig = AdminApiSignUtil.sign("test-secret", ts, "POST", "/admin/import", 7L, body);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/import");
        request.setContent(body);
        request.addHeader(AdminApiAuthHeaders.USER_ID, "7");
        request.addHeader(AdminApiAuthHeaders.TIMESTAMP, String.valueOf(ts));
        request.addHeader(AdminApiAuthHeaders.SIGNATURE, sig);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    public void badSignature_unauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/import");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        request.addHeader(AdminApiAuthHeaders.USER_ID, "7");
        request.addHeader(AdminApiAuthHeaders.TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        request.addHeader(AdminApiAuthHeaders.SIGNATURE, "deadbeef");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }
}
