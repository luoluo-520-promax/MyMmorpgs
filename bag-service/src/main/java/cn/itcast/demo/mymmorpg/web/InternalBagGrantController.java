package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import cn.itcast.demo.mymmorpg.service.BagService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用内部发货（商城订单等），支持幂等键防重复发奖。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "bag-service")
@RequestMapping("/internal/bag")
public class InternalBagGrantController {

    private final BagService bagService;

    public InternalBagGrantController(BagService bagService) {
        this.bagService = bagService;
    }

    @PostMapping("/grant")
    public ResponseEntity<Integer> grant(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestBody Map<String, Object> body) {
        String idempotencyKey = body.get("idempotencyKey") == null ? "" : String.valueOf(body.get("idempotencyKey"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawRewards = (List<Map<String, Object>>) body.get("rewards");
        List<ItemReward> rewards = new ArrayList<>();
        if (rawRewards != null) {
            for (Map<String, Object> item : rawRewards) {
                int itemId = ((Number) item.get("itemId")).intValue();
                int count = ((Number) item.get("count")).intValue();
                rewards.add(ItemReward.newBuilder().setItemId(itemId).setCount(count).build());
            }
        }
        int rc = bagService.grantItemsIdempotent(playerId, idempotencyKey, rewards);
        return ResponseEntity.ok(rc);
    }

    /** 按道具模板 ID 批量扣减（公会创建等）。 */
    @PostMapping("/consume")
    public ResponseEntity<Integer> consume(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawCosts = (List<Map<String, Object>>) body.get("costs");
        Map<Integer, Integer> costs = new java.util.LinkedHashMap<>();
        if (rawCosts != null) {
            for (Map<String, Object> item : rawCosts) {
                int itemId = ((Number) item.get("itemId")).intValue();
                int count = ((Number) item.get("count")).intValue();
                costs.merge(itemId, count, Integer::sum);
            }
        }
        return ResponseEntity.ok(bagService.consumeItemsByConfig(playerId, costs));
    }
}
