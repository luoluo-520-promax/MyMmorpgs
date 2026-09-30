package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.CreateShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopOrderCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopPurchaseHistoryCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetShopShelfCsReq;
import cn.itcast.demo.mymmorpg.service.ShopService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 协议转发内部 API（Feign remote=true 时由 player-service 调用）。
 */
@RestController
@RequestMapping("/internal/shop")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "shop-service")
public class InternalShopCommandController {

    private final ShopService shopService;

    public InternalShopCommandController(ShopService shopService) {
        this.shopService = shopService;
    }

    @PostMapping(value = "/shelf", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> shelf(@RequestHeader("X-Player-Id") long playerId,
                                        @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = shopService.handleGetShelf(playerId, GetShopShelfCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/order/create", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> create(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = shopService.handleCreateOrder(playerId, CreateShopOrderCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/order/get", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> get(@RequestHeader("X-Player-Id") long playerId,
                                      @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = shopService.handleGetOrder(playerId, GetShopOrderCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }

    @PostMapping(value = "/history", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> history(@RequestHeader("X-Player-Id") long playerId,
                                          @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = shopService.handlePurchaseHistory(playerId, GetShopPurchaseHistoryCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }
}
