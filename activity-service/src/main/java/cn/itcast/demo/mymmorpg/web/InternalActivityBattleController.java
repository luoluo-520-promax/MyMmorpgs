package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.ActivityBattleProgressService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗结束投影内部 API（MQ 关闭时可由 battle-service HTTP 回调）。
 */
@RestController
@RequestMapping("/internal/activity")
public class InternalActivityBattleController {

    private final ActivityBattleProgressService battleProgressService;

    public InternalActivityBattleController(ActivityBattleProgressService battleProgressService) {
        this.battleProgressService = battleProgressService;
    }

    @PostMapping("/battle-end")
    public Map<String, Object> battleEnd(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestBody Map<String, Object> body) {
        long battleId = body.get("battleId") instanceof Number n ? n.longValue() : 0L;
        int result = body.get("result") instanceof Number n ? n.intValue() : 0;
        List<Long> updated = battleProgressService.applyBattleEnd(playerId, battleId, result);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("updatedActivityIds", updated);
        return resp;
    }
}
