package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.ChatHarassmentService;
import cn.itcast.demo.mymmorpg.service.CustomChatChannelService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 自定义频道、超链接解析、拉黑与骚扰积分内部 API。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "chat-service")
@RequestMapping("/internal/chat")
public class InternalChatSocialController {

    private final CustomChatChannelService customChatChannelService;
    private final ChatHarassmentService chatHarassmentService;

    public InternalChatSocialController(CustomChatChannelService customChatChannelService,
                                        ChatHarassmentService chatHarassmentService) {
        this.customChatChannelService = customChatChannelService;
        this.chatHarassmentService = chatHarassmentService;
    }

    @PostMapping("/channel/create")
    public Map<String, Object> createChannel(@RequestHeader("X-Player-Id") long playerId,
                                             @RequestParam String name,
                                             @RequestParam(defaultValue = "INTEREST") String type) {
        return customChatChannelService.createChannel(playerId, name, type);
    }

    @PostMapping("/channel/join")
    public Map<String, Object> joinChannel(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam String channelId) {
        return customChatChannelService.joinChannel(playerId, channelId);
    }

    @PostMapping("/channel/leave")
    public Map<String, Object> leaveChannel(@RequestHeader("X-Player-Id") long playerId,
                                            @RequestParam String channelId) {
        return customChatChannelService.leaveChannel(playerId, channelId);
    }

    @PostMapping("/channel/send")
    public Map<String, Object> sendChannel(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestParam String channelId,
                                           @RequestBody(required = false) Map<String, Object> body) {
        String content = body == null ? "" : String.valueOf(body.getOrDefault("content", ""));
        return customChatChannelService.send(playerId, channelId, content);
    }

    @GetMapping("/channel/mine")
    public Map<String, Object> myChannels(@RequestHeader("X-Player-Id") long playerId) {
        return customChatChannelService.listMyChannels(playerId);
    }

    @PostMapping("/hyperlinks/parse")
    public Map<String, Object> parseLinks(@RequestBody(required = false) Map<String, Object> body) {
        String content = body == null ? "" : String.valueOf(body.getOrDefault("content", ""));
        List<Map<String, Object>> links = CustomChatChannelService.parseHyperlinks(content);
        return Map.of("ok", true, "hyperlinks", links);
    }

    @PostMapping("/block")
    public Map<String, Object> block(@RequestHeader("X-Player-Id") long playerId,
                                     @RequestParam long targetId) {
        return chatHarassmentService.block(playerId, targetId);
    }

    @PostMapping("/unblock")
    public Map<String, Object> unblock(@RequestHeader("X-Player-Id") long playerId,
                                       @RequestParam long targetId) {
        return chatHarassmentService.unblock(playerId, targetId);
    }

    @GetMapping("/block/list")
    public Map<String, Object> blockList(@RequestHeader("X-Player-Id") long playerId) {
        return chatHarassmentService.listBlocked(playerId);
    }

    @GetMapping("/harassment/score")
    public Map<String, Object> harassmentScore(@RequestParam long playerId) {
        return chatHarassmentService.scoreOf(playerId);
    }
}
