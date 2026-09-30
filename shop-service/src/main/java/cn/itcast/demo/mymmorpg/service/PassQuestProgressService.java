package cn.itcast.demo.mymmorpg.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 任务完成 → 战令经验投影。
 */
@Service
public class PassQuestProgressService {

    private final PassService passService;
    private final int seasonId;
    private final int questXp;

    public PassQuestProgressService(PassService passService,
                                    @Value("${game.pass.season-id:1}") int seasonId,
                                    @Value("${game.pass.quest-xp:100}") int questXp) {
        this.passService = passService;
        this.seasonId = Math.max(1, seasonId);
        this.questXp = Math.max(0, questXp);
    }

    public Map<String, Object> onQuestCompleted(long playerId, int questId) {
        if (playerId <= 0 || questId <= 0) {
            return Map.of("ok", false, "error", "invalid_args");
        }
        Map<String, Object> view = passService.addDailyXp(playerId, seasonId, questXp);
        return Map.of(
                "ok", true,
                "playerId", playerId,
                "questId", questId,
                "xpAdded", questXp,
                "pass", view);
    }
}
