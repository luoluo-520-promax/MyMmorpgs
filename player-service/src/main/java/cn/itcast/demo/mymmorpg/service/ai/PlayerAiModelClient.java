package cn.itcast.demo.mymmorpg.service.ai;

import cn.itcast.demo.mymmorpg.config.PlayerAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Component
@EnableConfigurationProperties(PlayerAiProperties.class)
public class PlayerAiModelClient {

    private static final String SYSTEM_PROMPT = """
            你是 MyMmorpg 玩家顾问。根据「当前玩家画像」与「全服统计」给出个性化建议。
            用中文 Markdown，引用具体数字（胜率、使用率、等级、战力）；不要编造未提供的数据；
            不要要求玩家提供密码或完整支付信息；建议应可执行（学技能、带治疗道具、先打低难度怪等）。
            """;

    private final PlayerAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PlayerAiModelClient(PlayerAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isAvailable() {
        return properties.isLlmEnabled() && StringUtils.hasText(properties.getApiKey());
    }

    public Optional<String> polishAdvice(String contextMarkdown, String question) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", properties.getModel());
            body.put("temperature", 0.3);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
            messages.addObject().put("role", "user").put("content",
                    "玩家问题：\n" + question + "\n\n数据上下文：\n" + contextMarkdown);
            String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(5_000L, properties.getTimeoutMs())))
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(content.asText().trim());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
