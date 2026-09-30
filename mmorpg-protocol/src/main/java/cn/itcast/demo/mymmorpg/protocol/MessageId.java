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
    /** 创建角色请求 */
    public static final int CREATE_PLAYER_CS_REQ = 7;
    /** 创建角色响应 */
    public static final int CREATE_PLAYER_SC_RSP = 8;
    /** 查询角色属性/天赋请求 */
    public static final int GET_CHARACTER_INFO_CS_REQ = 9;
    /** 查询角色属性/天赋响应 */
    public static final int GET_CHARACTER_INFO_SC_RSP = 10;
    /** 分配天赋点请求 */
    public static final int ALLOCATE_TALENT_CS_REQ = 11;
    /** 分配天赋点响应 */
    public static final int ALLOCATE_TALENT_SC_RSP = 12;
    /** 消耗金币请求（经济系统） */
    public static final int SPEND_GOLD_CS_REQ = 13;
    /** 消耗金币响应 */
    public static final int SPEND_GOLD_SC_RSP = 14;
    /** 踢线通知（服务端主动推送：重复登录/运维强制等） */
    public static final int KICK_PLAYER_SC_NOTIFY = 15;
    /** 刷新登录票据请求 */
    public static final int RENEW_TICKET_CS_REQ = 16;
    /** 刷新登录票据响应 */
    public static final int RENEW_TICKET_SC_RSP = 17;

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
    /** 跨场景传送请求（经 Center 路由） */
    public static final int TRANSFER_SCENE_CS_REQ = 112;
    /** 跨场景传送响应 */
    public static final int TRANSFER_SCENE_SC_RSP = 113;
    /** 进入遭遇战请求（路线 B 转场） */
    public static final int ENTER_ENCOUNTER_CS_REQ = 114;
    /** 进入遭遇战响应 */
    public static final int ENTER_ENCOUNTER_SC_RSP = 115;
    /** 进入遭遇战推送（Loading/切战场） */
    public static final int ENTER_ENCOUNTER_SC_NOTIFY = 116;
    /** 退出遭遇战请求（回世界） */
    public static final int EXIT_ENCOUNTER_CS_REQ = 117;
    /** 退出遭遇战响应 */
    public static final int EXIT_ENCOUNTER_SC_RSP = 118;
    /** 退出遭遇战推送 */
    public static final int EXIT_ENCOUNTER_SC_NOTIFY = 119;
    /** 断线重连恢复场景请求 */
    public static final int RESUME_SCENE_CS_REQ = 120;
    /** 断线重连恢复场景响应 */
    public static final int RESUME_SCENE_SC_RSP = 121;

    // -------- 2xx：状态同步战斗（module = {@link Modules#BATTLE}） --------
    /** 开始战斗请求 */
    public static final int BATTLE_START_CS_REQ = 201;
    /** 开始战斗响应 */
    public static final int BATTLE_START_SC_RSP = 202;
    /** 战斗操作请求（普攻、技能等） */
    public static final int BATTLE_ACTION_CS_REQ = 203;
    /** 战斗操作响应 */
    public static final int BATTLE_ACTION_SC_RSP = 204;
    /** 完美闪避请求（ACTION_PERFECT_DODGE） */
    public static final int ACTION_PERFECT_DODGE_CS_REQ = 208;
    /** 完美闪避响应 */
    public static final int ACTION_PERFECT_DODGE_SC_RSP = 209;
    /** 弹反/招架请求（ACTION_PARRY） */
    public static final int ACTION_PARRY_CS_REQ = 210;
    /** 弹反/招架响应 */
    public static final int ACTION_PARRY_SC_RSP = 211;
    /** 下落重击请求（ACTION_FALL_HEAVY） */
    public static final int ACTION_FALL_HEAVY_CS_REQ = 212;
    /** 下落重击响应 */
    public static final int ACTION_FALL_HEAVY_SC_RSP = 213;
    /** 受击确认推送（HIT_CONFIRM，含 stagger_level） */
    public static final int HIT_CONFIRM_SC_NOTIFY = 214;
    /** 预输入清空推送（INPUT_CLEAR） */
    public static final int INPUT_CLEAR_SC_NOTIFY = 215;
    /** 处决触发推送（EXECUTION_TRIGGER） */
    public static final int EXECUTION_TRIGGER_SC_NOTIFY = 216;
    /** 稀有精英出现推送（RARE_ELITE_APPEAR） */
    public static final int RARE_ELITE_APPEAR_SC_NOTIFY = 217;
    /** 区域觉醒推送（REGION_MASTERY / EPIC_MVP） */
    public static final int REGION_MASTERY_SC_NOTIFY = 218;
    /** 幻影引导标记推送（PHANTOM_GUIDE_MARK） */
    public static final int PHANTOM_GUIDE_MARK_SC_NOTIFY = 219;
    /** 命中反馈推送（HitStop / CameraShake） */
    public static final int BATTLE_HIT_FEEDBACK_SC_NOTIFY = 2090;
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
    /** 穿戴装备请求 */
    public static final int EQUIP_ITEM_CS_REQ = 713;
    /** 穿戴装备响应 */
    public static final int EQUIP_ITEM_SC_RSP = 714;
    /** 卸下装备请求 */
    public static final int UNEQUIP_ITEM_CS_REQ = 715;
    /** 卸下装备响应 */
    public static final int UNEQUIP_ITEM_SC_RSP = 716;

    // -------- 5xx：大厅（好友/邮件/排行）（module = {@link Modules#HALL}） --------
    public static final int GET_FRIEND_LIST_CS_REQ = 501;
    public static final int GET_FRIEND_LIST_SC_RSP = 502;
    public static final int ADD_FRIEND_CS_REQ = 503;
    public static final int ADD_FRIEND_SC_RSP = 504;
    public static final int GET_MAIL_LIST_CS_REQ = 505;
    public static final int GET_MAIL_LIST_SC_RSP = 506;
    public static final int CLAIM_MAIL_CS_REQ = 507;
    public static final int CLAIM_MAIL_SC_RSP = 508;
    public static final int GET_RANKING_CS_REQ = 509;
    public static final int GET_RANKING_SC_RSP = 510;

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

    // -------- 9xx：客户端版本/资源更新（module = {@link Modules#UPDATE}） --------
    /** 获取版本清单与补丁计划请求 */
    public static final int GET_VERSION_MANIFEST_CS_REQ = 901;
    /** 获取版本清单与补丁计划响应 */
    public static final int GET_VERSION_MANIFEST_SC_RSP = 902;
    /** 校验本地资源 checksum 请求 */
    public static final int VERIFY_RESOURCE_CHECKSUM_CS_REQ = 903;
    /** 校验本地资源 checksum 响应 */
    public static final int VERIFY_RESOURCE_CHECKSUM_SC_RSP = 904;

    // -------- 10xx：任务（module = {@link Modules#QUEST}） --------
    public static final int GET_QUEST_LIST_CS_REQ = 1001;
    public static final int GET_QUEST_LIST_SC_RSP = 1002;
    public static final int ACCEPT_QUEST_CS_REQ = 1003;
    public static final int ACCEPT_QUEST_SC_RSP = 1004;
    public static final int CLAIM_QUEST_REWARD_CS_REQ = 1005;
    public static final int CLAIM_QUEST_REWARD_SC_RSP = 1006;

    // -------- 11xx：匹配（module = {@link Modules#MATCH}） --------
    public static final int ENQUEUE_MATCH_CS_REQ = 1101;
    public static final int ENQUEUE_MATCH_SC_RSP = 1102;
    public static final int CANCEL_MATCH_CS_REQ = 1103;
    public static final int CANCEL_MATCH_SC_RSP = 1104;
    public static final int MATCH_STATUS_SC_NOTIFY = 1105;

    // -------- 12xx：皮肤（module = {@link Modules#SKIN}） --------
    public static final int GET_WARDROBE_CS_REQ = 1201;
    public static final int GET_WARDROBE_SC_RSP = 1202;
    public static final int EQUIP_SKIN_CS_REQ = 1203;
    public static final int EQUIP_SKIN_SC_RSP = 1204;
    public static final int UNEQUIP_SKIN_CS_REQ = 1205;
    public static final int UNEQUIP_SKIN_SC_RSP = 1206;
    /** 命座解锁请求（MSG_CONSTELLATION_UNLOCK） */
    public static final int CONSTELLATION_UNLOCK_CS_REQ = 1210;
    /** 命座解锁响应 */
    public static final int CONSTELLATION_UNLOCK_SC_RSP = 1211;
    /** 属性重算推送（AttributeRecalc） */
    public static final int ATTRIBUTE_RECALC_SC_NOTIFY = 1212;

    // -------- 13xx：挑战（module = {@link Modules#CHALLENGE}） --------
    public static final int START_CHALLENGE_CS_REQ = 1301;
    public static final int START_CHALLENGE_SC_RSP = 1302;
    public static final int FINISH_CHALLENGE_CS_REQ = 1303;
    public static final int FINISH_CHALLENGE_SC_RSP = 1304;

    // -------- 14xx：抽卡（module = {@link Modules#GACHA}） --------
    public static final int GET_GACHA_INFO_CS_REQ = 1401;
    public static final int GET_GACHA_INFO_SC_RSP = 1402;
    public static final int DO_GACHA_CS_REQ = 1403;
    public static final int DO_GACHA_SC_RSP = 1404;
    public static final int EXCHANGE_GACHA_CEILING_CS_REQ = 1405;
    public static final int EXCHANGE_GACHA_CEILING_SC_RSP = 1406;
    public static final int GET_GACHA_HISTORY_CS_REQ = 1407;
    public static final int GET_GACHA_HISTORY_SC_RSP = 1408;

    // -------- 15xx：肉鸽（module = {@link Modules#ROGUE}） --------
    public static final int START_ROGUE_CS_REQ = 1501;
    public static final int START_ROGUE_SC_RSP = 1502;
    public static final int GET_ROGUE_INFO_CS_REQ = 1503;
    public static final int GET_ROGUE_INFO_SC_RSP = 1504;
    public static final int ROGUE_MOVE_CS_REQ = 1505;
    public static final int ROGUE_MOVE_SC_RSP = 1506;
    public static final int ROGUE_SELECT_BLESSING_CS_REQ = 1507;
    public static final int ROGUE_SELECT_BLESSING_SC_RSP = 1508;
    public static final int ROGUE_QUIT_CS_REQ = 1509;
    public static final int ROGUE_QUIT_SC_RSP = 1510;
    /** 肉鸽遭遇开战推送 */
    public static final int ROGUE_ENCOUNTER_SC_NOTIFY = 1511;
    public static final int ROGUE_ALLOCATE_TALENT_CS_REQ = 1512;
    public static final int ROGUE_ALLOCATE_TALENT_SC_RSP = 1513;

    // -------- 16xx：商城（module = {@link Modules#SHOP}） --------
    public static final int GET_SHOP_SHELF_CS_REQ = 1601;
    public static final int GET_SHOP_SHELF_SC_RSP = 1602;
    public static final int CREATE_SHOP_ORDER_CS_REQ = 1603;
    public static final int CREATE_SHOP_ORDER_SC_RSP = 1604;
    public static final int GET_SHOP_ORDER_CS_REQ = 1605;
    /** 支付结果查询响应 / 履约成功推送（文档约定均为 1606） */
    public static final int GET_SHOP_ORDER_SC_RSP = 1606;
    public static final int SHOP_ORDER_PAID_SC_NOTIFY = 1606;
    public static final int GET_SHOP_PURCHASE_HISTORY_CS_REQ = 1607;
    public static final int GET_SHOP_PURCHASE_HISTORY_SC_RSP = 1608;

    // -------- 20xx / 22xx / 24xx：大世界 P10 扩展通知（跨模块体验指令，不严格走 module*100） --------
    /** 生物预警推送（CREATURE_ALERT，亲密度协助） */
    public static final int CREATURE_ALERT_SC_NOTIFY = 2200;
    /** 钩锁拉拽敌人请求（GRAPPLE_PULL_ENEMY） */
    public static final int GRAPPLE_PULL_ENEMY_CS_REQ = 2210;
    /** 钩锁拉拽敌人响应 */
    public static final int GRAPPLE_PULL_ENEMY_SC_RSP = 2211;
    /** 载具释放技能请求（VehicleCastSkillReq） */
    public static final int VEHICLE_CAST_SKILL_CS_REQ = 2220;
    /** 载具释放技能响应 */
    public static final int VEHICLE_CAST_SKILL_SC_RSP = 2221;
    /** 队长快速标记请求（QuickMarkCommand） */
    public static final int QUICK_MARK_CS_REQ = 2230;
    /** 全队 AOI 标记目标推送（MARK_TARGET） */
    public static final int MARK_TARGET_SC_NOTIFY = 2231;
    /** 全服强制过场推送（FORCED_CUTSCENE_START） */
    public static final int FORCED_CUTSCENE_START_SC_NOTIFY = 2400;
    /** 个人传说任务实例开始（带选项对话 UI） */
    public static final int STORY_INSTANCE_START_SC_NOTIFY = 2450;
    /** 联机访客世界隔膜特效（WORLD_SHIFT_SHIELD） */
    public static final int WORLD_SHIFT_SHIELD_SC_NOTIFY = 2451;
    /** 房主关键剧情互斥锁：访客 UI 屏蔽层（UI_BLOCK_LAYER） */
    public static final int UI_BLOCK_LAYER_SC_NOTIFY = 2452;
    /** 联机剧情旁观同步（SyncCutsceneState） */
    public static final int SYNC_CUTSCENE_STATE_SC_NOTIFY = 2453;
    /** 主机迁移广播（HOST_TRANSFER） */
    public static final int HOST_TRANSFER_SC_NOTIFY = 2454;
    /** 地形状态向量全量同步（TerrainStateVector） */
    public static final int TERRAIN_STATE_VECTOR_SC_NOTIFY = 2455;
    /** 冷门区域稀有度提升提示（探索热力图调度） */
    public static final int EXPLORE_LOOT_RARITY_BOOST_SC_NOTIFY = 2260;
    /** 伙伴路径节点下发（客户端插值跟随） */
    public static final int COMPANION_PATH_NODES_SC_NOTIFY = 2501;

    // -------- 25xx：P15 生成式交互扩展（跨模块体验指令） --------
    /** AI 常驻伙伴对话气泡推送（含 TTS 情绪标记） */
    public static final int COMPANION_DIALOGUE_SC_NOTIFY = 2500;

    // -------- 26xx：P19 沉浸感微观物理与文明时态 --------
    /** 植被压弯（BEND_VEGETATION） */
    public static final int BEND_VEGETATION_SC_NOTIFY = 2600;
    /** 动态脚印/车辙（FOOTPRINT_SPAWN） */
    public static final int FOOTPRINT_SPAWN_SC_NOTIFY = 2601;
    /** 投射物/碎石残留（DEBRIS_SPAWN） */
    public static final int DEBRIS_SPAWN_SC_NOTIFY = 2602;
    /** 预测物理增量（PREDICTED_PHYSICS_DELTA） */
    public static final int PREDICTED_PHYSICS_DELTA_SC_NOTIFY = 2603;
    /** 感官干扰（PERCEPTION_MODIFIER） */
    public static final int PERCEPTION_MODIFIER_SC_NOTIFY = 2604;
    /** 光线适应曲线（EYE_ADAPTATION） */
    public static final int EYE_ADAPTATION_SC_NOTIFY = 2605;

    /**
     * 私有构造器，禁止实例化。
     */
    private MessageId() { // 仅通过常量名引用 msgId
    }
}
