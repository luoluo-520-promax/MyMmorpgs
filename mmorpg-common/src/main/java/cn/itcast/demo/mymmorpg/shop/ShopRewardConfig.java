package cn.itcast.demo.mymmorpg.shop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopRewardConfig {
    public int itemId;
    public int count;
    public int bindType;
}
