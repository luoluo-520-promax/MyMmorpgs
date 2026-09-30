package cn.itcast.demo.mymmorpg.model.ai;

/**
 * 玩家个人战斗战绩摘要（进程内聚合）。
 */
public class PlayerBattleLite {

    private long playerId;
    private long endedTotal;
    private long winCount;
    private long loseCount;
    private long drawCount;
    private double winRate;
    private long skillCastTotal;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public long getEndedTotal() {
        return endedTotal;
    }

    public void setEndedTotal(long endedTotal) {
        this.endedTotal = endedTotal;
    }

    public long getWinCount() {
        return winCount;
    }

    public void setWinCount(long winCount) {
        this.winCount = winCount;
    }

    public long getLoseCount() {
        return loseCount;
    }

    public void setLoseCount(long loseCount) {
        this.loseCount = loseCount;
    }

    public long getDrawCount() {
        return drawCount;
    }

    public void setDrawCount(long drawCount) {
        this.drawCount = drawCount;
    }

    public double getWinRate() {
        return winRate;
    }

    public void setWinRate(double winRate) {
        this.winRate = winRate;
    }

    public long getSkillCastTotal() {
        return skillCastTotal;
    }

    public void setSkillCastTotal(long skillCastTotal) {
        this.skillCastTotal = skillCastTotal;
    }
}
