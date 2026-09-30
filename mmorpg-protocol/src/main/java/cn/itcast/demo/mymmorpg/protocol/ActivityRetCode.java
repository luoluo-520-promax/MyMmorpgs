/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/ActivityRetCode.java
 * 类型：类
 * 职责：定义活动系统专用返回码，与活动相关 Protobuf 响应中的 retcode 字段一致。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 声明当前类所属包，供其他模块 import 引用

/**
 * 活动系统 retcode 常量表（0 表示成功，非 0 表示具体失败原因）。
 * <p>用于 {@code GET_ACTIVITY_LIST}、{@code CLAIM_ACTIVITY_REWARD} 等活动接口响应。</p>
 */
public final class ActivityRetCode { // final 禁止被继承，仅作为常量容器使用

    /** 操作成功，活动请求已正常处理 */
    public static final int OK = 0; // 与通用 RetCode.OK 语义一致，表示无错误
    /** 活动不存在：活动 ID 无效或配置表中未找到 */
    public static final int ACTIVITY_NOT_FOUND = 1; // 客户端传入的 activityId 无对应配置
    /** 活动已关闭：不在开放时间窗口内或运营侧已下线 */
    public static final int ACTIVITY_CLOSED = 2; // 活动状态为关闭，不可参与或领奖
    /** 奖励已领取：玩家此前已成功领取过该档位奖励 */
    public static final int REWARD_ALREADY_CLAIMED = 3; // 防重复领奖校验失败
    /** 条件未满足：登录天数、等级、任务进度等参与条件不达标 */
    public static final int CONDITION_NOT_MET = 4; // 领奖前置条件校验未通过
    /** 背包已满：发放奖励道具时背包无空余格子 */
    public static final int BAG_FULL = 5; // 与 BagRetCode.BAG_FULL 语义相同，活动发奖失败
    /** 奖励档位无效：rewardIndex 越界或该活动无此奖励索引 */
    public static final int INVALID_REWARD_INDEX = 6; // 客户端请求的奖励下标不合法

    /**
     * 私有构造器，禁止外部 new 实例。
     * <p>本类仅提供 static final 常量，不需要也不允许创建对象。</p>
     */
    private ActivityRetCode() { // 工具类惯用写法，隐藏默认 public 构造器
    }
}
