package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.reaction.BattleReactionService;
import cn.itcast.demo.mymmorpg.world.battle.BattleReplayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 战斗瞬时博弈 + 指令流重播内部 API。
 */
@RestController
@RequestMapping("/internal/battle")
public class InternalBattleReactionController {

    private final BattleReactionService reactions;
    private final BattleReplayService replays;

    public InternalBattleReactionController(
            BattleReactionService reactions, BattleReplayService replays) {
        this.reactions = reactions == null ? new BattleReactionService() : reactions;
        this.replays = replays == null ? new BattleReplayService() : replays;
    }

    public InternalBattleReactionController() {
        this(new BattleReactionService(), new BattleReplayService());
    }

    @PostMapping("/reaction/open-window")
    public Map<String, Object> openWindow(@RequestBody Map<String, Object> body) {
        return reactions.openWindow(
                String.valueOf(body.get("attackId")),
                asLong(body.get("attackerEntityId")),
                System.currentTimeMillis());
    }

    @PostMapping("/reaction/perfect-dodge")
    public Map<String, Object> perfectDodge(@RequestBody Map<String, Object> body) {
        return reactions.perfectDodge(
                String.valueOf(body.getOrDefault("battleId", "local")),
                asLong(body.get("playerId")),
                String.valueOf(body.get("attackId")),
                asLong(body.getOrDefault("clientTs", System.currentTimeMillis())),
                System.currentTimeMillis());
    }

    @PostMapping("/reaction/parry")
    public Map<String, Object> parry(@RequestBody Map<String, Object> body) {
        return reactions.parry(
                String.valueOf(body.getOrDefault("battleId", "local")),
                asLong(body.get("playerId")),
                String.valueOf(body.get("attackId")),
                asLong(body.getOrDefault("clientTs", System.currentTimeMillis())),
                System.currentTimeMillis());
    }

    @PostMapping("/replay/start")
    public Map<String, Object> replayStart(@RequestBody Map<String, Object> body) {
        return replays.start(
                body.get("battleId") == null ? null : String.valueOf(body.get("battleId")),
                asLong(body.getOrDefault("seed", 1L)),
                System.currentTimeMillis());
    }

    @PostMapping("/replay/playback")
    public Map<String, Object> replayPlayback(@RequestBody Map<String, Object> body) {
        return replays.playback(
                String.valueOf(body.get("replayId")),
                asDouble(body.getOrDefault("speedMul", 1d)),
                null, null);
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 1d;
        }
    }
}
