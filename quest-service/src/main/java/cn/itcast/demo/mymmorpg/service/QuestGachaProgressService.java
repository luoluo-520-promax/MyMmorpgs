package cn.itcast.demo.mymmorpg.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 抽卡事件 → 任务进度。
 */
@Service
public class QuestGachaProgressService {

    private final QuestProgressService questProgressService;

    public QuestGachaProgressService(QuestProgressService questProgressService) {
        this.questProgressService = questProgressService;
    }

    public List<Integer> applyGachaDraw(long playerId, int bannerId, int times) {
        if (playerId <= 0 || times <= 0) {
            return Collections.emptyList();
        }
        return questProgressService.advanceByEvent(
                playerId, QuestProgressService.EVENT_GACHA_DRAW, times);
    }
}
