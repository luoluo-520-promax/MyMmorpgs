package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 活动界面显示文本（多语言键值或直出文案）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityDisplayTextPayload {

    public String title = "";
    public String subtitle = "";
    public String ruleText = "";
    public String buttonText = "";
    /** 扩展文案键值，如 tips、footer 等。 */
    public Map<String, String> extras = new LinkedHashMap<>();
}
