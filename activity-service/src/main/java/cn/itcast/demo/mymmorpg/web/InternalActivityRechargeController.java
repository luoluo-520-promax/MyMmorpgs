package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.ActivityShopRechargeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城订单支付成功后累加首充活动进度（HTTP 同步入口）。
 */
@RestController
@RequestMapping("/internal/activity")
@ConditionalOnProperty(name = "spring.application.name", havingValue = "activity-service")
public class InternalActivityRechargeController {

    private final ActivityShopRechargeService rechargeService;

    public InternalActivityRechargeController(ActivityShopRechargeService rechargeService) {
        this.rechargeService = rechargeService;
    }

    @PostMapping("/recharge")
    public Map<String, Object> addRecharge(@RequestHeader("X-Player-Id") long playerId,
                                           @RequestBody Map<String, Object> body) {
        long delta = body.get("amount") == null ? 0L : ((Number) body.get("amount")).longValue();
        String orderId = body.get("orderId") == null ? "" : String.valueOf(body.get("orderId"));
        List<Long> updated = rechargeService.applyRecharge(playerId, delta, orderId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("orderId", orderId);
        resp.put("amount", delta);
        resp.put("activityIds", updated);
        resp.put("idempotent", updated.isEmpty() && playerId > 0 && delta > 0 && !orderId.isBlank());
        return resp;
    }
}
