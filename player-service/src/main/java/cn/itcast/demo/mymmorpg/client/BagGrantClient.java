package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/** 通用背包幂等发货（抽卡/商城等）。 */
@FeignClient(name = "bag-service", contextId = "bagGrantClient",
        configuration = InternalApiFeignConfiguration.class)
public interface BagGrantClient {

    @PostMapping(value = "/internal/bag/grant", consumes = MediaType.APPLICATION_JSON_VALUE)
    Integer grant(@RequestHeader("X-Player-Id") long playerId, @RequestBody Map<String, Object> body);
}
