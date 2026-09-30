package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 活动 JSON 导入文档：含活动 ID、类型与完整配置字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityImportDocument extends ActivityConfigPayload {

    /** 活动 ID；导入时若已存在则更新。 */
    public Long id;
    /** 活动类型。 */
    public Integer type;
    /** 是否开启。 */
    public Boolean opened = true;
}
