package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 活动界面资源引用（客户端资源包路径或 CDN URL）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityUiResourcePayload {

    public String bannerUrl = "";
    public String iconUrl = "";
    public String backgroundUrl = "";
    /** UI 预制体/面板资源包路径。 */
    public String uiPrefabPath = "";
    /** 关联客户端资源版本号，用于热更校验。 */
    public long resourceVersion;
}
