package cn.itcast.demo.mymmorpg.port.remote;

import cn.itcast.demo.mymmorpg.config.InternalApiAuthProperties;
import cn.itcast.demo.mymmorpg.security.InternalApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.InternalApiSignUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 为 RestTemplate 内部 Port 调用附加 HMAC 签名头。
 */
public final class InternalApiRestClient {

    private final RestTemplate restTemplate;
    private final InternalApiAuthProperties authProperties;

    public InternalApiRestClient(RestTemplate restTemplate, InternalApiAuthProperties authProperties) {
        this.restTemplate = restTemplate;
        this.authProperties = authProperties;
    }

    public <T> ResponseEntity<T> exchange(String url, HttpMethod method, long playerId, Object body, Class<T> responseType) {
        byte[] rawBody = serializeBody(body);
        HttpHeaders headers = buildHeaders(method.name(), extractPath(url), playerId, rawBody);
        if (body != null && !(body instanceof byte[])) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<?> entity = rawBody.length == 0
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(rawBody, headers);
        return restTemplate.exchange(url, method, entity, responseType);
    }

    public <T> T get(String url, long playerId, Class<T> responseType) {
        return exchange(url, HttpMethod.GET, playerId, null, responseType).getBody();
    }

    public <T> T postJson(String url, long playerId, Object body, Class<T> responseType) {
        return exchange(url, HttpMethod.POST, playerId, body, responseType).getBody();
    }

    public void postJsonVoid(String url, long playerId, Object body) {
        exchange(url, HttpMethod.POST, playerId, body, Void.class);
    }

    private HttpHeaders buildHeaders(String method, String path, long playerId, byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(InternalApiAuthHeaders.PLAYER_ID, Long.toString(playerId));
        if (!authProperties.isEnabled() || authProperties.getSecret() == null || authProperties.getSecret().isBlank()) {
            return headers;
        }
        long timestamp = System.currentTimeMillis();
        String signature = InternalApiSignUtil.sign(
                authProperties.getSecret(), timestamp, method, path, playerId, body);
        headers.set(InternalApiAuthHeaders.TIMESTAMP, Long.toString(timestamp));
        headers.set(InternalApiAuthHeaders.SIGNATURE, signature);
        return headers;
    }

    private static byte[] serializeBody(Object body) {
        if (body == null) {
            return new byte[0];
        }
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException("序列化请求体失败", e);
        }
    }

    private static String extractPath(String url) {
        int idx = url.indexOf("/internal/");
        if (idx >= 0) {
            int q = url.indexOf('?', idx);
            return q > 0 ? url.substring(idx, q) : url.substring(idx);
        }
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int pathStart = url.indexOf('/', scheme + 3);
        if (pathStart < 0) {
            return "/";
        }
        int q = url.indexOf('?', pathStart);
        return q > 0 ? url.substring(pathStart, q) : url.substring(pathStart);
    }
}
