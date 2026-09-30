package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.InternalApiFeignConfiguration;
import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.model.ai.PlayerBattleLite;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** player-service 拉取战斗统计（不依赖 admin-service 的 BattleStatsClient）。 */
@FeignClient(name = "battle-service", contextId = "playerBattleStatsClient",
        configuration = InternalApiFeignConfiguration.class)
public interface PlayerBattleStatsClient {

    @GetMapping("/internal/battle/stats/snapshot")
    BattleStatsSnapshot snapshot();

    @GetMapping("/internal/battle/stats/player/{playerId}")
    PlayerBattleLite playerStats(@PathVariable("playerId") long playerId);
}
