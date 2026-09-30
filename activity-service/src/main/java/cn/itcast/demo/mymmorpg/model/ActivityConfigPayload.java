/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/model/ActivityConfigPayload.java
 * 2) 所属模块：activity-service / model
 * 3) 主要职责：反序列化 activity.data 列 JSON，承载活动完整运营配置
 * 4) 系统位置：领域模型层，被 ActivityService 解析活动配置时使用
 * 5) 变更建议：JSON 字段扩展时保持 @JsonIgnoreProperties 以兼容旧数据
 */
package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * activity.data 列 JSON 结构（活动展示、玩法、奖励、商店、UI 等完整配置）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityConfigPayload {

    /** 活动开始时间戳（毫秒）。 */
    public long startTime;
    /** 活动结束时间戳；默认极大值表示未配置结束。 */
    public long endTime = Long.MAX_VALUE;
    /** 活动显示名称。 */
    public String name = "活动";
    /** 列表页简短描述。 */
    public String briefDesc = "";
    /** 详情页完整描述。 */
    public String description = "";
    /** 玩法说明。 */
    public String gameplay = "";
    /** 规则说明。 */
    public String rules = "";
    /** 活动配置版本号，客户端对比后决定是否拉取新配置。 */
    public long configVersion = 1;
    /** 通用活动条件列表。 */
    public List<ActivityConditionPayload> conditions = new ArrayList<>();
    /** 关卡/阶段配置。 */
    public List<ActivityStagePayload> stages = new ArrayList<>();
    /** 消耗与参与限制。 */
    public ActivityCostLimitPayload costLimit = new ActivityCostLimitPayload();
    /** 多档奖励列表。 */
    public List<RewardTierPayload> rewardTiers = new ArrayList<>();
    /** 奖励发放方式，见 {@link ActivityRewardMethod}。 */
    public int rewardMethod = ActivityRewardMethod.DIRECT_TO_BAG;
    /** 活动积分/代币配置。 */
    public ActivityTokenPayload token = new ActivityTokenPayload();
    /** 关联商店 ID，0 表示无活动商店。 */
    public long shopId;
    /** 活动商店商品列表。 */
    public List<ActivityShopProductPayload> shopProducts = new ArrayList<>();
    /** 界面资源引用。 */
    public ActivityUiResourcePayload uiResources = new ActivityUiResourcePayload();
    /** 界面显示文本。 */
    public ActivityDisplayTextPayload displayText = new ActivityDisplayTextPayload();
    /** 仅白名单账号可见（灰度）。 */
    public boolean whiteListOnly;
    /** 白名单玩家 ID。 */
    public java.util.List<Long> whiteListPlayerIds = new java.util.ArrayList<>();
    /** DRAFT / PUBLISHED；草稿不对全服生效。 */
    public String publishStatus = "PUBLISHED";
}
