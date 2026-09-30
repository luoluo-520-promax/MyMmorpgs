package cn.itcast.demo.mymmorpg.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * 统一 LLM HTTP 客户端：超时、重试、Token 计量；失败返回 empty 供调用方降级模板。
 */
public final class LlmHttpClient {

    public record Config(
            String baseUrl,
            String apiKey,
            String model,
            long timeoutMs,
            int maxRetries,
            AiCostMeter costMeter) {
    }

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Config config;

    public LlmHttpClient(ObjectMapper objectMapper, Config config) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.config = config;
        long connectMs = Math.min(10_000L, Math.max(1_000L, config.timeoutMs()));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();
    }

    public boolean isAvailable() {
        return config.apiKey() != null && !config.apiKey().isBlank();
    }

    public Optional<String> chat(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        if (config.costMeter() != null && config.costMeter().isOverBudget()) {
            config.costMeter().recordFailure();
            return Optional.empty();
        }
        int retries = Math.max(0, config.maxRetries());
        Exception last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                Optional<String> out = doChat(systemPrompt, userPrompt, jsonMode);
                if (out.isPresent()) {
                    return out;
                }
            } catch (Exception e) {
                last = e;
            }
            if (attempt < retries) {
                sleepQuiet(50L * (attempt + 1));
            }
        }
        if (config.costMeter() != null) {
            config.costMeter().recordFailure();
        }
        if (last != null) {
            // swallowed: callers fall back to templates
        }
        return Optional.empty();
    }

    private Optional<String> doChat(String systemPrompt, String userPrompt, boolean jsonMode) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.model());
        body.put("temperature", 0.2);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt == null ? "" : systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt == null ? "" : userPrompt);
        if (jsonMode) {
            body.putObject("response_format").put("type", "json_object");
        }

        String url = config.baseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(1_000L, config.timeoutMs())))
                .header("Authorization", "Bearer " + config.apiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            return Optional.empty();
        }
        String text = stripCodeFence(content.asText().trim());
        if (config.costMeter() != null) {
            long promptEst = AiCostMeter.estimateTokens(systemPrompt) + AiCostMeter.estimateTokens(userPrompt);
            long completionEst = AiCostMeter.estimateTokens(text);
            JsonNode usage = root.path("usage");
            if (usage.has("prompt_tokens")) {
                promptEst = usage.path("prompt_tokens").asLong(promptEst);
            }
            if (usage.has("completion_tokens")) {
                completionEst = usage.path("completion_tokens").asLong(completionEst);
            }
            config.costMeter().recordSuccess(promptEst, completionEst);
        }
        return Optional.of(text);
    }

    private static String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3).trim();
            }
        }
        return t;
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
