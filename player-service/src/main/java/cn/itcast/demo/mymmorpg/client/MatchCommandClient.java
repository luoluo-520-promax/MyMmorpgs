package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "matchmaking-service", contextId = "matchCommandClient", configuration = InternalApiFeignConfiguration.class)
public interface MatchCommandClient {

    @PostMapping(value = "/internal/match/enqueue", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] enqueue(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/match/cancel", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] cancel(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);
}
