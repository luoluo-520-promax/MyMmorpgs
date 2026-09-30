package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.service.QuestGachaProgressService;
import cn.itcast.demo.mymmorpg.service.QuestProgressService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务进度事件入口：抽卡 / 通用事件推进。
 */
@RestController
@ConditionalOnBean(QuestProgressService.class)
@RequestMapping("/internal/quest/progress")
public class InternalQuestProgressController {

    private final QuestProgressService questProgressService;
    private final QuestGachaProgressService gachaProgressService;

    public InternalQuestProgressController(QuestProgressService questProgressService,
                                           QuestGachaProgressService gachaProgressService) {
        this.questProgressService = questProgressService;
        this.gachaProgressService = gachaProgressService;
    }

    @PostMapping("/gacha")
    public Map<String, Object> gacha(@RequestHeader("X-Player-Id") long playerId,
                                     @RequestParam(defaultValue = "0") int bannerId,
                                     @RequestParam(defaultValue = "1") int times) {
        List<Integer> advanced = gachaProgressService.applyGachaDraw(playerId, bannerId, times);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("advancedQuestIds", advanced);
        return out;
    }

    @PostMapping("/event")
    public Map<String, Object> event(@RequestHeader("X-Player-Id") long playerId,
                                     @RequestParam String eventType,
                                     @RequestParam(defaultValue = "1") int delta) {
        List<Integer> advanced = questProgressService.advanceByEvent(playerId, eventType, delta);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("advancedQuestIds", advanced);
        return out;
    }
}
