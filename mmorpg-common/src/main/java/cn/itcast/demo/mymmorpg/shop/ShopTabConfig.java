package cn.itcast.demo.mymmorpg.shop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopTabConfig {
    public String tabId = "";
    public String name = "";
    public int sort;
    public boolean opened = true;
}
