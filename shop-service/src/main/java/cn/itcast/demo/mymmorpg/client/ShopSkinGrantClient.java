package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.ShopInternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "player-service", contextId = "shopSkinGrantClient",
        configuration = ShopInternalApiFeignConfiguration.class)
public interface ShopSkinGrantClient {

    @PostMapping(value = "/internal/player/skin/unlock-by-item", consumes = MediaType.APPLICATION_JSON_VALUE)
    Integer unlockByItem(@RequestHeader("X-Player-Id") long playerId, @RequestBody Map<String, Object> body);

    @PostMapping(value = "/internal/player/skin/grant", consumes = MediaType.APPLICATION_JSON_VALUE)
    Integer grant(@RequestHeader("X-Player-Id") long playerId, @RequestBody Map<String, Object> body);
}
