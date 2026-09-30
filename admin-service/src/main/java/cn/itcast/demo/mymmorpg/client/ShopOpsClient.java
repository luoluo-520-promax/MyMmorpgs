package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "shop-service", contextId = "shopOpsClient", configuration = AdminFeignConfiguration.class)
public interface ShopOpsClient {

    @PostMapping(value = "/internal/shop/orders/{orderId}/refund", consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> refund(@PathVariable("orderId") String orderId, @RequestBody Map<String, Object> body);

    @PostMapping("/internal/shop/orders/{orderId}/fulfill")
    Map<String, Object> fulfill(@PathVariable("orderId") String orderId);

    @PostMapping("/internal/shop/reload")
    Map<String, Object> reload();
}
