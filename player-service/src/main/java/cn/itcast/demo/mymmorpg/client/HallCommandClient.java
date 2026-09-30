package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "hall-service", contextId = "hallCommandClient",
        configuration = InternalApiFeignConfiguration.class,
        fallbackFactory = HallCommandClientFallbackFactory.class)
public interface HallCommandClient {

    @PostMapping(value = "/internal/hall/friends", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] friends(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/hall/friends/add", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] addFriend(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/hall/mails", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] mails(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/hall/mails/claim", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] claimMail(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/hall/ranking", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] ranking(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);
}
