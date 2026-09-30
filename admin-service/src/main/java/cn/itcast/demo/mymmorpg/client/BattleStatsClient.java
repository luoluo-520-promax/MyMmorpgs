package cn.itcast.demo.mymmorpg.client;

import cn.itcast.demo.mymmorpg.config.AdminFeignConfiguration;
import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.model.ai.PlayerBattleLite;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "battle-service", contextId = "battleStatsClient", configuration = AdminFeignConfiguration.class)
public interface BattleStatsClient {

    @GetMapping("/internal/battle/stats/snapshot")
    BattleStatsSnapshot snapshot();

    @GetMapping("/internal/battle/stats/player/{playerId}")
    PlayerBattleLite playerStats(@PathVariable("playerId") long playerId);
}
