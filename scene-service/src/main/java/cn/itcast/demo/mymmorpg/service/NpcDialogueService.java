package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.config.NpcAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 智能 NPC：优先 LLM 自由对话，失败回退规则线索。
 */
@Service
@EnableConfigurationProperties(NpcAiProperties.class)
public class NpcDialogueService {

    private final NpcAiProperties properties;
    private final ObjectMapper objectMapper;
    private final NpcDialogueMemory memory;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public NpcDialogueService(NpcAiProperties properties, ObjectMapper objectMapper, NpcDialogueMemory memory) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.memory = memory;
    }

    public Map<String, Object> talk(long playerId, String npcId, String message) {
        if (!properties.isEnabled()) {
            return Map.of("ok", false, "error", "npc_ai_disabled");
        }
        String npc = npcId == null || npcId.isBlank() ? "guide" : npcId.trim();
        String msg = message == null ? "" : message.trim();
        long now = System.currentTimeMillis();
        NpcDialogueMemory.Mood mood = memory.mood(playerId, npc, now);
        Optional<String> llm = tryLlm(npc, msg, memory.recentTurns(playerId, npc, now));
        Map<String, Object> out;
        if (llm.isPresent()) {
            out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("playerId", playerId);
            out.put("npcId", npc);
            out.put("reply", memory.moodPrefix(mood) + llm.get());
            out.put("clues", List.of());
            out.put("source", "llm");
            out.put("mood", mood.name());
        } else {
            out = ruleReply(playerId, npc, msg, mood);
        }
        memory.remember(playerId, npc, msg, String.valueOf(out.get("reply")), now);
        out.put("memoryTurns", memory.recentTurns(playerId, npc, now).size());
        return out;
    }

    private Map<String, Object> ruleReply(long playerId, String npcId, String message, NpcDialogueMemory.Mood mood) {
        String lower = message.toLowerCase(Locale.ROOT);
        List<String> clues = new ArrayList<>();
        String reply;
        if (lower.contains("记得") || lower.contains("刚才")) {
            List<Map<String, Object>> turns = memory.recentTurns(playerId, npcId, System.currentTimeMillis());
            reply = turns.isEmpty()
                    ? "我们才刚见面，还没有太多回忆。"
                    : "我记得你刚才说：「" + turns.get(Math.max(0, turns.size() - 2)).get("text") + "」。";
        } else if (lower.contains("任务") || lower.contains("quest") || lower.contains("线索")) {
            reply = "向导低声道：东侧遗迹有微光，或许与主线任务相关。";
            clues.add("scene:ruins-east");
            clues.add("quest:main-01");
        } else if (lower.contains("故事") || lower.contains("背景") || lower.contains("lore")) {
            reply = "很久以前，星轨碎片坠落于此，旅人因此汇聚成城。";
            clues.add("lore:starfall");
        } else if (lower.contains("boss") || lower.contains("世界")) {
            reply = "世界 BOSS 苏醒时，全服旅人可临时组队共同挑战。";
            clues.add("activity:world-boss");
        } else if (msgBlank(message)) {
            reply = "你好，旅人。想听听任务线索、世界传说，还是世界 BOSS 的事？";
        } else {
            reply = "我是「" + npcId + "」。你可以问我任务、故事或世界事件。";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("playerId", playerId);
        out.put("npcId", npcId);
        out.put("reply", memory.moodPrefix(mood) + reply);
        out.put("clues", clues);
        out.put("source", "rule");
        out.put("mood", mood.name());
        return out;
    }

    private static boolean msgBlank(String message) {
        return message == null || message.isBlank();
    }

    private Optional<String> tryLlm(String npcId, String message, List<Map<String, Object>> history) {
        if (!properties.isLlmEnabled() || !StringUtils.hasText(properties.getApiKey())) {
            return Optional.empty();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", properties.getModel());
            body.put("temperature", 0.7);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content",
                    "你是 MyMmorpg 场景 NPC「" + npcId + "」，用中文简短回复，可提供任务线索或背景故事，不要输出 Markdown。");
            if (history != null) {
                for (Map<String, Object> turn : history) {
                    String role = "user".equals(turn.get("role")) ? "user" : "assistant";
                    messages.addObject().put("role", role).put("content", String.valueOf(turn.getOrDefault("text", "")));
                }
            }
            messages.addObject().put("role", "user").put("content", message.isBlank() ? "打个招呼" : message);
            String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(3000L, properties.getTimeoutMs())))
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
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
