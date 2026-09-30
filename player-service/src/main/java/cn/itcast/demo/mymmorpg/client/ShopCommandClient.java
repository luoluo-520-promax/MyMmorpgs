package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "shop-service", contextId = "shopCommandClient", configuration = InternalApiFeignConfiguration.class)
public interface ShopCommandClient {

    @PostMapping(value = "/internal/shop/shelf",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] shelf(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/shop/order/create",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] createOrder(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/shop/order/get",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] getOrder(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);

    @PostMapping(value = "/internal/shop/history",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] history(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body);
}
