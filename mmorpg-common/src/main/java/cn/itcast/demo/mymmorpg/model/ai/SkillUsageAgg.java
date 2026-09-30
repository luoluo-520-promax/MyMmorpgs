package cn.itcast.demo.mymmorpg.model.ai;

/**
 * 全服技能使用统计。
 */
public class SkillUsageAgg {

    private int skillId;
    private long castCount;
    private double usageRate;

    public int getSkillId() {
        return skillId;
    }

    public void setSkillId(int skillId) {
        this.skillId = skillId;
    }

    public long getCastCount() {
        return castCount;
    }

    public void setCastCount(long castCount) {
        this.castCount = castCount;
    }

    public double getUsageRate() {
        return usageRate;
    }

    public void setUsageRate(double usageRate) {
        this.usageRate = usageRate;
    }
}
