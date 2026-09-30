package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import cn.itcast.demo.mymmorpg.service.BagService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 供 activity-service 远程调用的背包发奖 Port 内部 API。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "bag-service")
@RequestMapping("/internal/bag/activity")
public class InternalBagActivityController {

    private final BagService bagService;

    public InternalBagActivityController(BagService bagService) {
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
        int rc = bagService.grantItemsForActivity(playerId, idempotencyKey, rewards);
        return ResponseEntity.ok(rc);
    }
}
