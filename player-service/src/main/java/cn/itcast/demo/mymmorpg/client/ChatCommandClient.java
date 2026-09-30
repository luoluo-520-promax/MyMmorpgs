package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "chat-service", contextId = "chatCommandClient", configuration = InternalApiFeignConfiguration.class)
public interface ChatCommandClient {

    @PostMapping(value = "/internal/chat/send", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] send(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);
}
