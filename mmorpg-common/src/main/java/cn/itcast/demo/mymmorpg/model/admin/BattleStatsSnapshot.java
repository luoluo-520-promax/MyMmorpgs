package cn.itcast.demo.mymmorpg.model.admin;

import cn.itcast.demo.mymmorpg.model.ai.SkillUsageAgg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗进程内统计快照，供 admin AI 周报与玩家 AI 顾问只读查询。
 */
public class BattleStatsSnapshot {

    private long collectedSinceEpochMs;
    private long snapshotEpochMs;
    private long issuedBattleIdMax;
    private int activeBattleBindings;
    private long endedTotal;
    private long winCount;
    private long loseCount;
    private long drawCount;
    private double winRate;
    private double avgDurationSec;
    private long maxActiveBindingsObserved;
    private long skillCastTotal;
    private Map<Integer, MonsterAgg> byMonsterTemplate = new LinkedHashMap<>();
    private List<SkillUsageAgg> topSkillUsage = new ArrayList<>();
    private List<String> notes = new ArrayList<>();

    public long getCollectedSinceEpochMs() {
        return collectedSinceEpochMs;
    }

    public void setCollectedSinceEpochMs(long collectedSinceEpochMs) {
        this.collectedSinceEpochMs = collectedSinceEpochMs;
    }

    public long getSnapshotEpochMs() {
        return snapshotEpochMs;
    }

    public void setSnapshotEpochMs(long snapshotEpochMs) {
        this.snapshotEpochMs = snapshotEpochMs;
    }

    public long getIssuedBattleIdMax() {
        return issuedBattleIdMax;
    }

    public void setIssuedBattleIdMax(long issuedBattleIdMax) {
        this.issuedBattleIdMax = issuedBattleIdMax;
    }

    public int getActiveBattleBindings() {
        return activeBattleBindings;
    }

    public void setActiveBattleBindings(int activeBattleBindings) {
        this.activeBattleBindings = activeBattleBindings;
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

    public double getAvgDurationSec() {
        return avgDurationSec;
    }

    public void setAvgDurationSec(double avgDurationSec) {
        this.avgDurationSec = avgDurationSec;
    }

    public long getMaxActiveBindingsObserved() {
        return maxActiveBindingsObserved;
    }

    public void setMaxActiveBindingsObserved(long maxActiveBindingsObserved) {
        this.maxActiveBindingsObserved = maxActiveBindingsObserved;
    }

    public Map<Integer, MonsterAgg> getByMonsterTemplate() {
        return byMonsterTemplate;
    }

    public void setByMonsterTemplate(Map<Integer, MonsterAgg> byMonsterTemplate) {
        this.byMonsterTemplate = byMonsterTemplate;
    }

    public long getSkillCastTotal() {
        return skillCastTotal;
    }

    public void setSkillCastTotal(long skillCastTotal) {
        this.skillCastTotal = skillCastTotal;
    }

    public List<SkillUsageAgg> getTopSkillUsage() {
        return topSkillUsage;
    }

    public void setTopSkillUsage(List<SkillUsageAgg> topSkillUsage) {
        this.topSkillUsage = topSkillUsage;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }

    public static class MonsterAgg {
        private int monsterTemplateId;
        private long total;
        private long wins;
        private double winRate;

        public int getMonsterTemplateId() {
            return monsterTemplateId;
        }

        public void setMonsterTemplateId(int monsterTemplateId) {
            this.monsterTemplateId = monsterTemplateId;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getWins() {
            return wins;
        }

        public void setWins(long wins) {
            this.wins = wins;
        }

        public double getWinRate() {
            return winRate;
        }

        public void setWinRate(double winRate) {
            this.winRate = winRate;
        }
    }
}
