/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/model/ActivityConfigPayload.java
 * 2) 所属模块：activity-service / model
 * 3) 主要职责：反序列化 activity.data 列 JSON，承载活动时间、名称与奖励档位配置
 * 4) 系统位置：领域模型层，被 ActivityService 解析活动配置时使用
 * 5) 变更建议：JSON 字段扩展时保持 @JsonIgnoreProperties 以兼容旧数据
 */
package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 反序列化时忽略未知 JSON 字段

import java.util.ArrayList; // 可变列表，默认奖励档位集合
import java.util.List; // 列表接口

/**
 * activity.data 列 JSON 结构（活动展示时间、名称、档位等）。
 */
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略 JSON 中未映射的字段，便于向前兼容
public class ActivityConfigPayload { // 对应 MySQL activity 表 data 列反序列化后的对象

    /** 活动开始时间戳（毫秒）。 */
    public long startTime; // 活动起始时刻，用于计算三态与签到天数
    /** 活动结束时间戳；默认极大值表示未配置结束。 */
    public long endTime = Long.MAX_VALUE; // 活动结束时刻，默认永不结束
    /** 活动显示名称。 */
    public String name = "活动"; // 列表与详情展示用名称
    /** 列表页简短描述。 */
    public String briefDesc = ""; // 活动列表页摘要文案
    /** 多档奖励列表。 */
    public List<RewardTierPayload> rewardTiers = new ArrayList<>(); // 各奖励档位配置
} // ActivityConfigPayload 类结束
