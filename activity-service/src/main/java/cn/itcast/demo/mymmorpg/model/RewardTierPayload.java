/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/model/RewardTierPayload.java
 * 2) 所属模块：activity-service / model
 * 3) 主要职责：描述活动配置 JSON 中单档奖励的结构（道具、达标条件等）
 * 4) 系统位置：领域模型层，嵌套在 ActivityConfigPayload.rewardTiers 中
 * 5) 变更建议：首充用 targetRecharge，签到用 signDay，二者互斥使用
 */
package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 反序列化时忽略未知字段

/**
 * 单档奖励配置：首充用 targetRecharge，签到用 signDay。
 */
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略 JSON 中未映射字段
public class RewardTierPayload { // 活动配置 JSON 里「一档奖励」的结构

    /** 档位编号，客户端领奖、已领判断都靠它。 */
    public int index; // 档位唯一序号
    /** 奖励道具配置 ID。 */
    public int itemId; // 发放到背包的道具 ID
    /** 奖励数量。 */
    public int count; // 道具发放数量
    /** 首充达标金额；非首充档可为 null。 */
    public Integer targetRecharge; // 首充类活动达标门槛，null 表示不适用
    /** 签到第几天可领；非签到档可为 null。 */
    public Integer signDay; // 签到类活动第几天可领，null 表示不适用
} // RewardTierPayload 类结束
