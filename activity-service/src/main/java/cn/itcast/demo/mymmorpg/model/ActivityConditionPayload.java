package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 通用活动条件：等级、VIP、充值、自定义表达式等。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityConditionPayload {

    /** 条件类型：LEVEL_MIN、VIP_MIN、RECHARGE_MIN、STAGE_UNLOCK、CUSTOM。 */
    public String type = "";
    /** 数值型条件阈值。 */
    public int intValue;
    /** 字符串型条件参数。 */
    public String stringValue = "";
}
