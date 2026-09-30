package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.ChatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公会系统同步聊天 roster：创建公会 / 成员加入退出。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "chat-service")
@RequestMapping("/internal/chat/guild")
public class InternalChatGuildController {

    private final ChatService chatService;

    public InternalChatGuildController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/created")
    public Map<String, Object> created(@RequestBody Map<String, Object> body) {
        String guildId = String.valueOf(body.getOrDefault("guildId", ""));
        chatService.onGuildCreated(guildId);
        return Map.of("ok", true, "guildId", guildId);
    }

    @PostMapping("/member/sync")
    public Map<String, Object> syncMember(@RequestBody Map<String, Object> body) {
        long playerId = ((Number) body.getOrDefault("playerId", 0L)).longValue();
        Object guildRaw = body.get("guildId");
        String guildId = guildRaw == null ? "" : String.valueOf(guildRaw);
        boolean leave = Boolean.TRUE.equals(body.get("leave")) || guildId.isBlank();
        chatService.setPlayerGuild(playerId, leave ? null : guildId);
        return Map.of("ok", true, "playerId", playerId, "guildId", leave ? "" : guildId);
    }
}
