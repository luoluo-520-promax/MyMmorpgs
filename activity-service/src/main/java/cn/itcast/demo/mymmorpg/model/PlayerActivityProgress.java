/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/model/PlayerActivityProgress.java
 * 2) 所属模块：activity-service / model
 * 3) 主要职责：描述玩家在某一活动下的进度快照（已领档位、充值额、签到天）
 * 4) 系统位置：领域模型层，序列化后存入 Redis（activity:prog:{playerId}:{activityId}）
 * 5) 变更建议：新增进度字段时保持 @JsonIgnoreProperties 兼容旧 Redis 数据
 */
package cn.itcast.demo.mymmorpg.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * Redis 中玩家活动进度（activity:prog:{playerId}:{activityId}）。
 */
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知 JSON 字段
public class PlayerActivityProgress { // 玩家在某一活动下的进度快照，序列化为 JSON 存 Redis

    /** 已领取的奖励档位 index 集合（防止重复领奖）。 */
    public Set<Integer> claimed = new HashSet<>(); // 已领档位编号集合
    /** 累计充值金额（首充类活动用）。 */
    public long rechargeAmount; // 首充达标判断依据
    /** 已签到的活动内第几天（签到类活动用）。 */
    public Set<Integer> signDays = new HashSet<>();
    /** 当前活动代币/积分余额。 */
    public int tokenAmount;
    /** 当前解锁阶段序号。 */
    public int currentStage;
    /** 活动期间累计战斗胜利次数（战斗结束 MQ 投影）。 */
    public int battleWins;
} // PlayerActivityProgress 类结束
