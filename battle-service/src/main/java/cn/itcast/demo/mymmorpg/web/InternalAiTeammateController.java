package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.AiTeammateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/battle/ai-teammate")
public class InternalAiTeammateController {

    private final AiTeammateService aiTeammateService;

    public InternalAiTeammateController(AiTeammateService aiTeammateService) {
        this.aiTeammateService = aiTeammateService;
    }

    @PostMapping("/spawn")
    public Map<String, Object> spawn(@RequestHeader("X-Player-Id") long playerId,
                                     @RequestParam String battleId,
                                     @RequestParam(required = false) String hint) {
        return aiTeammateService.spawn(battleId, playerId, hint);
    }

    @PostMapping("/command")
    public Map<String, Object> command(@RequestParam String battleId,
                                       @RequestParam String botId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        String cmd = body == null || body.get("command") == null ? "" : String.valueOf(body.get("command"));
        return aiTeammateService.command(battleId, botId, cmd);
    }

    @PostMapping("/tick-boss")
    public Map<String, Object> tickBoss(@RequestParam String battleId,
                                        @RequestParam String botId,
                                        @RequestParam(defaultValue = "1") double hpRatio,
                                        @RequestParam(defaultValue = "0") long threatTargetId) {
        return aiTeammateService.tickBoss(battleId, botId, hpRatio, threatTargetId);
    }

    @PostMapping("/configure-boss-tree")
    public Map<String, Object> configureBossTree(@RequestParam String botId,
                                                 @RequestBody(required = false) List<Map<String, Object>> nodes) {
        return aiTeammateService.configureBossTree(botId, nodes);
    }

    @PostMapping("/strategy")
    public Map<String, Object> setStrategy(@RequestParam String battleId,
                                           @RequestParam String botId,
                                           @RequestParam String strategy,
                                           @RequestBody(required = false) Map<String, Object> params) {
        return aiTeammateService.setStrategy(battleId, botId, strategy, params);
    }

    @PostMapping("/bt/load")
    public Map<String, Object> loadBt(@RequestParam String treeId,
                                      @RequestBody(required = false) List<Map<String, Object>> nodes) {
        return aiTeammateService.loadBehaviorTree(treeId, nodes);
    }

    @PostMapping("/bt/tick")
    public Map<String, Object> tickBt(@RequestParam String treeId,
                                      @RequestParam(defaultValue = "1") double hpRatio,
                                      @RequestParam(defaultValue = "0") long threatTargetId) {
        return aiTeammateService.tickBehaviorTree(treeId, hpRatio, threatTargetId);
    }

    @GetMapping("/bt/inspect")
    public Map<String, Object> inspectBt(@RequestParam String treeId) {
        return aiTeammateService.inspectBehaviorTree(treeId);
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam String battleId) {
        return aiTeammateService.status(battleId);
    }
}
