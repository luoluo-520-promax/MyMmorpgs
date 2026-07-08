/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/MessageId.java
 * 类型：类
 * 职责：定义与接口文档一致的全局命令 ID（二进制包头 msgId）。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 消息 ID 常量，编解码与路由共用

/**
 * 与接口文档一致的命令 ID（写入二进制包头）。
 * <p>命名约定：{@code XXX_CS_REQ} 客户端请求，{@code XXX_SC_RSP} 服务端响应，{@code XXX_SC_NOTIFY} 服务端推送。</p>
 * <p>数值规则：{@code msgId = module * 100 + cmd}，module 见 {@link Modules}。</p>
 */
public final class MessageId { // 不可继承的消息号表

    // -------- 0xx：登录 / 选角 / 登出（module = {@link Modules#AUTH}） --------
    /** 账号登录请求（客户端 → 服务端） */
    public static final int ACCOUNT_LOGIN_CS_REQ = 1; // cmd=1, module=0
    /** 账号登录响应（服务端 → 客户端） */
    public static final int ACCOUNT_LOGIN_SC_RSP = 2; // cmd=2
    /** 选择角色请求 */
    public static final int SELECT_PLAYER_CS_REQ = 3; // cmd=3
    /** 选择角色响应 */
    public static final int SELECT_PLAYER_SC_RSP = 4; // cmd=4
    /** 角色登出请求 */
    public static final int PLAYER_LOGOUT_CS_REQ = 5; // cmd=5
    /** 角色登出响应 */
    public static final int PLAYER_LOGOUT_SC_RSP = 6; // cmd=6

    // -------- 1xx：场景 Actor（module = {@link Modules#SCENE}） --------
    /** 进入场景请求 */
    public static final int ENTER_SCENE_CS_REQ = 101; // 1*100+1
    /** 进入场景响应 */
    public static final int ENTER_SCENE_SC_RSP = 102;
    /** 查询当前场景信息请求 */
    public static final int GET_CUR_SCENE_INFO_CS_REQ = 103;
    /** 查询当前场景信息响应 */
    public static final int GET_CUR_SCENE_INFO_SC_RSP = 104;
    /** 移动请求（位置同步） */
    public static final int MOVE_CS_REQ = 105;
    /** 移动响应 */
    public static final int MOVE_SC_RSP = 106;
    /** 实体同步推送（服务端主动下发周围实体） */
    public static final int SYNC_ENTITY_SC_NOTIFY = 107;
    /** 切换分线请求 */
    public static final int SWITCH_LINE_CS_REQ = 108;
    /** 切换分线响应 */
    public static final int SWITCH_LINE_SC_RSP = 109;
    /** 查询附近实体请求 */
    public static final int GET_NEARBY_ENTITIES_CS_REQ = 110;
    /** 查询附近实体响应 */
    public static final int GET_NEARBY_ENTITIES_SC_RSP = 111;

    // -------- 2xx：状态同步战斗（module = {@link Modules#BATTLE}） --------
    /** 开始战斗请求 */
    public static final int BATTLE_START_CS_REQ = 201;
    /** 开始战斗响应 */
    public static final int BATTLE_START_SC_RSP = 202;
    /** 战斗操作请求（普攻、技能等） */
    public static final int BATTLE_ACTION_CS_REQ = 203;
    /** 战斗操作响应 */
    public static final int BATTLE_ACTION_SC_RSP = 204;
    /** 战斗状态同步推送 */
    public static final int BATTLE_SYNC_SC_NOTIFY = 205;
    /** 战斗结束请求（客户端上报或确认） */
    public static final int BATTLE_END_CS_REQ = 206;
    /** 战斗结束响应 */
    public static final int BATTLE_END_SC_RSP = 207;

    // -------- 3xx：技能系统（module = {@link Modules#SKILL}） --------
    /** 查询玩家技能列表请求 */
    public static final int GET_PLAYER_SKILLS_CS_REQ = 301;
    /** 查询玩家技能列表响应 */
    public static final int GET_PLAYER_SKILLS_SC_RSP = 302;
    /** 学习技能请求 */
    public static final int LEARN_SKILL_CS_REQ = 303;
    /** 学习技能响应 */
    public static final int LEARN_SKILL_SC_RSP = 304;
    /** 释放技能请求（场景内） */
    public static final int CAST_SKILL_CS_REQ = 305;
    /** 释放技能响应 */
    public static final int CAST_SKILL_SC_RSP = 306;
    /** 技能冷却推送 */
    public static final int SKILL_COOLDOWN_SC_NOTIFY = 307;
    /** 技能学习结果推送 */
    public static final int SKILL_LEARN_SC_NOTIFY = 308;
    /** 技能数据就绪推送（登录后进游戏同步） */
    public static final int SKILL_DATA_READY_SC_NOTIFY = 309;

    // -------- 4xx：Buff 系统（module = {@link Modules#BUFF}） --------
    /** 查询实体 Buff 列表请求 */
    public static final int GET_ENTITY_BUFFS_CS_REQ = 401;
    /** 查询实体 Buff 列表响应 */
    public static final int GET_ENTITY_BUFFS_SC_RSP = 402;
    /** Buff 添加推送 */
    public static final int BUFF_ADD_SC_NOTIFY = 403;
    /** Buff 移除推送 */
    public static final int BUFF_REMOVE_SC_NOTIFY = 404;
    /** Buff 层数/时间更新推送 */
    public static final int BUFF_UPDATE_SC_NOTIFY = 405;
    /** 主动移除 Buff 请求 */
    public static final int REMOVE_BUFF_CS_REQ = 406;
    /** 主动移除 Buff 响应 */
    public static final int REMOVE_BUFF_SC_RSP = 407;

    // -------- 6xx：聊天系统（module = {@link Modules#CHAT}） --------
    /** 发送聊天消息请求 */
    public static final int SEND_CHAT_MSG_CS_REQ = 601;
    /** 发送聊天消息响应（retcode 见 {@link ChatRetCode}） */
    public static final int SEND_CHAT_MSG_SC_RSP = 602;
    /** 聊天消息广播推送 */
    public static final int CHAT_MSG_SC_NOTIFY = 603;

    // -------- 7xx：道具与背包（module = {@link Modules#BAG}） --------
    /** 查询背包信息请求 */
    public static final int GET_BAG_INFO_CS_REQ = 701;
    /** 查询背包信息响应 */
    public static final int GET_BAG_INFO_SC_RSP = 702;
    /** 使用道具请求 */
    public static final int USE_ITEM_CS_REQ = 703;
    /** 使用道具响应 */
    public static final int USE_ITEM_SC_RSP = 704;
    /** 丢弃道具请求 */
    public static final int DISCARD_ITEM_CS_REQ = 705;
    /** 丢弃道具响应 */
    public static final int DISCARD_ITEM_SC_RSP = 706;
    /** 背包排序请求 */
    public static final int SORT_BAG_CS_REQ = 707;
    /** 背包排序响应 */
    public static final int SORT_BAG_SC_RSP = 708;
    /** 出售道具请求 */
    public static final int SELL_ITEM_CS_REQ = 709;
    /** 出售道具响应 */
    public static final int SELL_ITEM_SC_RSP = 710;
    /** 背包数据就绪推送 */
    public static final int BAG_DATA_READY_SC_NOTIFY = 711;

    // -------- 8xx：活动系统（module = {@link Modules#ACTIVITY}） --------
    /** 查询活动列表请求 */
    public static final int GET_ACTIVITY_LIST_CS_REQ = 801;
    /** 查询活动列表响应 */
    public static final int GET_ACTIVITY_LIST_SC_RSP = 802;
    /** 查询活动详情请求 */
    public static final int GET_ACTIVITY_DETAIL_CS_REQ = 803;
    /** 查询活动详情响应 */
    public static final int GET_ACTIVITY_DETAIL_SC_RSP = 804;
    /** 领取活动奖励请求 */
    public static final int CLAIM_ACTIVITY_REWARD_CS_REQ = 805;
    /** 领取活动奖励响应 */
    public static final int CLAIM_ACTIVITY_REWARD_SC_RSP = 806;
    /** 活动状态变更推送 */
    public static final int ACTIVITY_STATUS_SC_NOTIFY = 807;
    /** 活动数据就绪推送 */
    public static final int ACTIVITY_DATA_READY_SC_NOTIFY = 808;

    /**
     * 私有构造器，禁止实例化。
     */
    private MessageId() { // 仅通过常量名引用 msgId
    }
}
