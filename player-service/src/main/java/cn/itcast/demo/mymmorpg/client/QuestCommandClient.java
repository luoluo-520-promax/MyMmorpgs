package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "quest-service", contextId = "questCommandClient", configuration = InternalApiFeignConfiguration.class)
public interface QuestCommandClient {

    @PostMapping(value = "/internal/quest/list", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] list(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/quest/accept", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] accept(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/quest/claim", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] claim(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);
}
