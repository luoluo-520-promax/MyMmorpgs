package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.QuestBattleProgressService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗结束 → 任务进度内部 API（MQ 关闭时的同步投影入口）。
 */
@RestController
@RequestMapping("/internal/quest")
public class InternalQuestBattleController {

    private final QuestBattleProgressService battleProgressService;

    public InternalQuestBattleController(QuestBattleProgressService battleProgressService) {
        this.battleProgressService = battleProgressService;
    }

    @PostMapping("/battle-end")
    public Map<String, Object> battleEnd(@RequestHeader("X-Player-Id") long playerId,
                                         @RequestBody Map<String, Object> body) {
        long battleId = body.get("battleId") instanceof Number n ? n.longValue() : 0L;
        int result = body.get("result") instanceof Number n ? n.intValue() : 0;
        List<Integer> advanced = battleProgressService.applyBattleEnd(playerId, battleId, result);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("advancedQuestIds", advanced);
        return resp;
    }
}
