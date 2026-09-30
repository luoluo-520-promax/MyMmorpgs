package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 活动商店商品条目。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityShopProductPayload {

    public int productId;
    public int itemId;
    public int count;
    /** 兑换所需活动代币数量。 */
    public int tokenCost;
    /** 每日限购，0 表示不限。 */
    public int dailyLimit;
}
