package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.quest.QuestConfigService;
import cn.itcast.demo.mymmorpg.quest.QuestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 按事件类型推进任务进度（BATTLE_WIN / SHOP_PAY / GACHA_DRAW）。
 */
@Service
public class QuestProgressService {

    public static final String EVENT_BATTLE_WIN = "BATTLE_WIN";
    public static final String EVENT_SHOP_PAY = "SHOP_PAY";
    public static final String EVENT_GACHA_DRAW = "GACHA_DRAW";

    private final QuestService questService;
    private final QuestConfigService questConfigService;

    public QuestProgressService(QuestService questService, QuestConfigService questConfigService) {
        this.questService = questService;
        this.questConfigService = questConfigService;
    }

    @Transactional
    public List<Integer> advanceByEvent(long playerId, String eventType, int delta) {
        List<Integer> advanced = new ArrayList<>();
        if (playerId <= 0 || delta <= 0 || eventType == null || eventType.isBlank()) {
            return advanced;
        }
        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        for (QuestTemplate tpl : questConfigService.listAll()) {
            String tplEvent = tpl.eventType == null || tpl.eventType.isBlank()
                    ? defaultEventForType(tpl.questType) : tpl.eventType.trim().toUpperCase(Locale.ROOT);
            if (!normalized.equals(tplEvent)) {
                continue;
            }
            questService.advanceProgress(playerId, tpl.questId, delta);
            advanced.add(tpl.questId);
        }
        return advanced;
    }

    private static String defaultEventForType(int questType) {
        // 日常默认按战斗胜利计数（兼容旧配置）
        if (questType == 3 || questType == 4) {
            return EVENT_BATTLE_WIN;
        }
        return "";
    }
}
