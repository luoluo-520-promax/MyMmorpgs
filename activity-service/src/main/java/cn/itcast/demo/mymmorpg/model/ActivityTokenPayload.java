package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 活动积分/代币配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityTokenPayload {

    /** 代币类型 ID。 */
    public int tokenId;
    /** 代币显示名称。 */
    public String tokenName = "";
    /** 初始赠送数量。 */
    public int initialAmount;
}
