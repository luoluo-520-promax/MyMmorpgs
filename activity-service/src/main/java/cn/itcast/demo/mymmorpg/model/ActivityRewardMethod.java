package cn.itcast.demo.mymmorpg.model;

/**
 * 活动奖励发放方式。
 */
public final class ActivityRewardMethod {

    /** 直接发放到背包。 */
    public static final int DIRECT_TO_BAG = 1;
    /** 通过邮件发放。 */
    public static final int MAIL = 2;
    /** 随机抽取其一。 */
    public static final int RANDOM_ONE = 3;
    /** 发放活动积分/代币。 */
    public static final int TOKEN_ONLY = 4;

    private ActivityRewardMethod() {
    }
}
