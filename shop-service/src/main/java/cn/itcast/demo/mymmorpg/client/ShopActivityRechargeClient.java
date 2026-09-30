package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.ShopInternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "activity-service", contextId = "shopActivityRechargeClient",
        configuration = ShopInternalApiFeignConfiguration.class)
public interface ShopActivityRechargeClient {

    @PostMapping(value = "/internal/activity/recharge", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> addRecharge(@RequestHeader("X-Player-Id") long playerId,
                                    @RequestBody Map<String, Object> body);
}
