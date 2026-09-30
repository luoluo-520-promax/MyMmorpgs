package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.GachaInternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/** 抽卡服调用背包幂等发货。 */
@FeignClient(name = "bag-service", contextId = "gachaBagGrantClient",
        configuration = GachaInternalApiFeignConfiguration.class)
public interface GachaBagGrantClient {

    @PostMapping(value = "/internal/bag/grant", consumes = MediaType.APPLICATION_JSON_VALUE)
    Integer grant(@RequestHeader("X-Player-Id") long playerId, @RequestBody Map<String, Object> body);
}
