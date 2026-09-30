package cn.itcast.demo.mymmorpg.center;

import cn.itcast.demo.mymmorpg.config.CenterRoutingProperties;
import cn.itcast.demo.mymmorpg.config.InternalApiAuthProperties;
import cn.itcast.demo.mymmorpg.security.InternalApiAuthHeaders;
import cn.itcast.demo.mymmorpg.security.InternalApiSignUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 通过 HTTP 查询远端中心服迁移计划：
 * {@code GET {baseUrl}/internal/center/plan?sceneId=}（兼容 planeId 别名）。
 */
@Component
public class HttpCenterRoutingClient {

    private final CenterRoutingProperties properties;
    private final InternalApiAuthProperties internalApiAuthProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpCenterRoutingClient(CenterRoutingProperties properties,
                                   InternalApiAuthProperties internalApiAuthProperties,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.internalApiAuthProperties = internalApiAuthProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500L, properties.getRemoteTimeoutMs())))
                .build();
    }

    public SceneMigrationPlan planMigration(int sceneId) {
        String base = properties.getRemoteBaseUrl() == null
                ? "" : properties.getRemoteBaseUrl().trim().replaceAll("/+$", "");
        if (base.isBlank()) {
            throw new IllegalStateException("game.center.remote-base-url is required when mode=remote");
        }
        try {
            String path = "/internal/center/plan?sceneId=" + sceneId + "&planeId=" + sceneId;
            URI uri = URI.create(base + path);
            long timeoutMs = Math.max(500L, properties.getRemoteTimeoutMs());
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET();
            signIfNeeded(builder, "GET", "/internal/center/plan");
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("remote center HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String nodeId = root.path("nodeId").asText("unknown");
            String host = root.path("nodeHost").asText("");
            int port = root.path("nodePort").asInt(0);
            int zoneId = root.path("zoneId").asInt(sceneId);
            boolean local = properties.getLocalNodeId() != null
                    && properties.getLocalNodeId().equalsIgnoreCase(nodeId);
            if (local) {
                return SceneMigrationPlan.localPlan(sceneId, nodeId, host, port);
            }
            return SceneMigrationPlan.remotePlan(sceneId, zoneId, nodeId, host, port);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("remote center planMigration failed: " + e.getMessage(), e);
        }
    }

    public void registerNode(String nodeId, String host, int port, java.util.List<Integer> sceneIds) {
        postJson("/internal/center/nodes/register", nodeBody(nodeId, host, port, sceneIds));
    }

    public void heartbeatNode(String nodeId, java.util.List<Integer> sceneIds) {
        postJson("/internal/center/nodes/heartbeat", nodeBody(nodeId, null, 0, sceneIds));
    }

    public void unregisterNode(String nodeId) {
        postJson("/internal/center/nodes/unregister", java.util.Map.of("nodeId", nodeId == null ? "" : nodeId));
    }

    private java.util.Map<String, Object> nodeBody(String nodeId, String host, int port,
                                                   java.util.List<Integer> sceneIds) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("nodeId", nodeId == null ? "" : nodeId);
        if (host != null) {
            body.put("host", host);
            body.put("port", port);
        }
        body.put("sceneIds", sceneIds == null ? java.util.List.of() : sceneIds);
        return body;
    }

    private void postJson(String path, Object body) {
        String base = properties.getRemoteBaseUrl() == null
                ? "" : properties.getRemoteBaseUrl().trim().replaceAll("/+$", "");
        if (base.isBlank()) {
            throw new IllegalStateException("game.center.remote-base-url is required when mode=remote");
        }
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            URI uri = URI.create(base + path);
            long timeoutMs = Math.max(500L, properties.getRemoteTimeoutMs());
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json));
            signIfNeeded(builder, "POST", path, json);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("remote center HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("remote center POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    private void signIfNeeded(HttpRequest.Builder builder, String method, String path) {
        signIfNeeded(builder, method, path, new byte[0]);
    }

    private void signIfNeeded(HttpRequest.Builder builder, String method, String path, byte[] body) {
        if (internalApiAuthProperties == null || !internalApiAuthProperties.isEnabled()) {
            return;
        }
        String secret = internalApiAuthProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        String signature = InternalApiSignUtil.sign(secret, timestamp, method, path, 0L,
                body == null ? new byte[0] : body);
        builder.header(InternalApiAuthHeaders.PLAYER_ID, "0");
        builder.header(InternalApiAuthHeaders.TIMESTAMP, Long.toString(timestamp));
        builder.header(InternalApiAuthHeaders.SIGNATURE, signature);
    }
}
