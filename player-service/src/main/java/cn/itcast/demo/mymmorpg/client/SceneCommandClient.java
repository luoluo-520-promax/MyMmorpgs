package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "scene-service", contextId = "sceneCommandClient",
        configuration = InternalApiFeignConfiguration.class,
        fallbackFactory = SceneCommandClientFallbackFactory.class)
public interface SceneCommandClient {

    @PostMapping(value = "/internal/scene/enter", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] enter(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/scene/cur", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] cur(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/scene/move", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] move(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/scene/switch-line", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] switchLine(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/scene/nearby", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] nearby(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/scene/transfer", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] transfer(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/scene/resume", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] resume(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/scene/leave")
    void leave(@RequestHeader("X-Player-Id") long playerId);

    @PostMapping(value = "/internal/scene/disconnect")
    void disconnect(@RequestHeader("X-Player-Id") long playerId);
}
