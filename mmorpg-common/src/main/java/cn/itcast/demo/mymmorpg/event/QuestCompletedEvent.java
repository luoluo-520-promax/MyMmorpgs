package cn.itcast.demo.mymmorpg.event;

import jforgame.commons.eventbus.BaseEvent;

/**
 * 任务进度达到完成态时发布，供功能解锁等订阅者消费。
 */
public class QuestCompletedEvent implements BaseEvent {

    private final long playerId;
    private final int questId;

    public QuestCompletedEvent(long playerId, int questId) {
        this.playerId = playerId;
        this.questId = questId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public int getQuestId() {
        return questId;
    }

    @Override
    public Object getOwner() {
        return playerId;
    }
}
