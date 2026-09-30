package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 活动消耗与参与限制。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityCostLimitPayload {

    /** 单次参与消耗道具 ID，0 表示无道具消耗。 */
    public int costItemId;
    /** 单次参与消耗道具数量。 */
    public int costItemCount;
    /** 每日参与次数上限，0 表示不限。 */
    public int dailyLimit;
    /** 总参与次数上限，0 表示不限。 */
    public int totalLimit;
    /** 最低 VIP 等级，0 表示不限。 */
    public int minVipLevel;
    /** 最低角色等级，0 表示不限。 */
    public int minPlayerLevel;
}
