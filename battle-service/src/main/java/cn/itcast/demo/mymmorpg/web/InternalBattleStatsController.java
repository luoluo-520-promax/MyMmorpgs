package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.model.ai.PlayerBattleLite;
import cn.itcast.demo.mymmorpg.service.BattleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部只读战斗统计（JSON），供 admin 周报与玩家 AI 顾问拉取。
 */
@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "battle-service")
@RequestMapping("/internal/battle/stats")
public class InternalBattleStatsController {

    private final BattleService battleService;

    public InternalBattleStatsController(BattleService battleService) {
        this.battleService = battleService;
    }

    @GetMapping("/snapshot")
    public BattleStatsSnapshot snapshot() {
        return battleService.statsSnapshot();
    }

    @GetMapping("/player/{playerId}")
    public PlayerBattleLite playerStats(@PathVariable long playerId) {
        return battleService.playerBattleStats(playerId);
    }
}
