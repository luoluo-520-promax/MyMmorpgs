package cn.itcast.demo.mymmorpg.event;

import jforgame.commons.eventbus.BaseEvent;

/**
 * 抽卡完成事件，供任务进度等订阅。
 */
public class GachaDrawEvent implements BaseEvent {

    private final long playerId;
    private final int bannerId;
    private final int times;

    public GachaDrawEvent(long playerId, int bannerId, int times) {
        this.playerId = playerId;
        this.bannerId = bannerId;
        this.times = times;
    }

    public long getPlayerId() {
        return playerId;
    }

    public int getBannerId() {
        return bannerId;
    }

    public int getTimes() {
        return times;
    }

    @Override
    public Object getOwner() {
        return playerId;
    }
}
