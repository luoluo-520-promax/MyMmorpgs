package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.QuestShopPayProgressService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城支付 → 任务进度内部 API（MQ 关闭时的同步投影入口）。
 */
@RestController
@RequestMapping("/internal/quest")
public class InternalQuestShopController {

    private final QuestShopPayProgressService shopPayProgressService;

    public InternalQuestShopController(QuestShopPayProgressService shopPayProgressService) {
        this.shopPayProgressService = shopPayProgressService;
    }

    @PostMapping("/shop-paid")
    public Map<String, Object> shopPaid(@RequestHeader("X-Player-Id") long playerId,
                                        @RequestBody Map<String, Object> body) {
        String orderId = body.get("orderId") == null ? "" : String.valueOf(body.get("orderId"));
        long amount = body.get("amount") instanceof Number n ? n.longValue() : 0L;
        List<Integer> advanced = shopPayProgressService.applyShopPaid(playerId, orderId, amount);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("advancedQuestIds", advanced);
        return resp;
    }
}
