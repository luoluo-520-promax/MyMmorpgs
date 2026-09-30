package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.GuildFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "chat-service", contextId = "guildChatClient",
        configuration = GuildFeignConfiguration.class)
public interface GuildChatClient {

    @PostMapping(value = "/internal/chat/guild/created", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> onCreated(@RequestBody Map<String, Object> body);

    @PostMapping(value = "/internal/chat/guild/member/sync", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> syncMember(@RequestBody Map<String, Object> body);
}
