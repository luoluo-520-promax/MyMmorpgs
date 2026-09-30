/**
 * 文件说明
 * 模块：mmorpg-common / 协议
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/protocol/RetCode.java
 * 类型：类
 * 职责：定义全局业务返回码（retcode），写入各模块 Protobuf 响应。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.protocol; // 全局错误码，各服务 Handler 统一引用

/**
 * 业务返回码（retcode）：0 成功，非 0 表示可预期的业务失败。
 * <p>与活动/背包/聊天等子系统专用码（{@link ActivityRetCode} 等）互补，覆盖登录、场景、战斗、技能、Buff 等。</p>
 */
public final class RetCode { // 工具类，仅含 static final 常量

    // -------- 通用 / 账号 --------
    /** 操作成功 */
    public static final int OK = 0; // 请求已正确处理，客户端可解析响应体
    /** 账号不存在 */
    public static final int ACCOUNT_NOT_FOUND = 1; // 登录名在账号库中无记录
    /** 密码错误 */
    public static final int PASSWORD_WRONG = 2; // 账号存在但凭证校验失败
    /** 角色不存在 */
    public static final int PLAYER_NOT_FOUND = 3; // playerId 无效或已删除
    /** 角色不属于当前账号 */
    public static final int PLAYER_NOT_BELONG_ACCOUNT = 4; // 选角时账号与角色归属不匹配
    /** 未登录或会话已失效 */
    public static final int NOT_LOGGED_IN = 5; // 需要先完成账号登录
    /** 服务器当前在线人数过多，暂时拒绝新登录 */
    public static final int SERVER_OVERLOADED = 6; // 网关或登录服限流
    /** 角色名已被占用 */
    public static final int PLAYER_NAME_TAKEN = 7;
    /** 账号下角色数量已达上限 */
    public static final int PLAYER_CREATE_LIMIT = 8;
    /** 金币不足 */
    public static final int GOLD_NOT_ENOUGH = 9;
    /** 天赋点不足 */
    public static final int TALENT_POINTS_NOT_ENOUGH = 10;
    /** 登录过于频繁（防暴力破解） */
    public static final int LOGIN_RATE_LIMITED = 11;
    /** 账号已被封禁 */
    public static final int ACCOUNT_BANNED = 12;
    /** 角色已被封禁 */
    public static final int PLAYER_BANNED = 13;
    /** 需要二次验证（异地/异常设备） */
    public static final int NEED_SECOND_AUTH = 14;
    /** 会话绑定校验失败（IP/UA 弱校验） */
    public static final int SESSION_BINDING_MISMATCH = 15;
    /** 客户端环境不可信（Root/Hook/篡改签名等） */
    public static final int CLIENT_UNTRUSTED = 16;
    /** 客户端版本过低，必须更新后才能登录 */
    public static final int CLIENT_VERSION_TOO_OLD = 17;
    /** 协议/战斗结构不兼容，强制跳转应用商店（VersionGateKeeper 硬性策略） */
    public static final int CLIENT_TOO_OLD = 601;
    /** 服务端内部未预期错误 */
    public static final int INTERNAL_ERROR = 99; // 异常兜底，不宜暴露细节给客户端

    // -------- 场景 / Actor --------
    /** 场景配置不存在 */
    public static final int SCENE_NOT_FOUND = 20; // sceneId 在配置表中无效
    /** 玩家当前不在任何场景 */
    public static final int NOT_IN_SCENE = 21; // 移动、战斗等操作要求已在场景内
    /** 线路 ID 无效或已满 */
    public static final int INVALID_LINE = 22; // 切线或进入时分线不可用
    /** 移动被拒绝（障碍、速度、距离等校验失败） */
    public static final int MOVE_REJECTED = 23; // 服务端校验移动包不合法
    /** 未选择角色，playerId 无效 */
    public static final int PLAYER_NOT_SELECTED = 24; // 已登录账号但未选角进入游戏
    /** 跨场景传送被拒绝 */
    public static final int SCENE_TRANSFER_REJECTED = 25;
    /** 跨节点迁移缺少目标节点广告地址 */
    public static final int SCENE_TRANSFER_NO_NODE = 26;
    /** 跨节点重定向：客户端应携带 session_ticket 重连 redirect_host:port */
    public static final int SCENE_TRANSFER_REDIRECT = 27;
    /** 迁移/进场票据无效或已过期 */
    public static final int SESSION_TICKET_INVALID = 28;
    /** 全部分线人数已满 */
    public static final int SCENE_LINE_FULL = 29;
    /** 断线重连快照过期或不存在 */
    public static final int RESUME_EXPIRED = 30;

    // -------- 大厅 / 社交 --------
    public static final int FRIEND_ALREADY_EXISTS = 80;
    public static final int FRIEND_NOT_FOUND = 81;
    public static final int MAIL_NOT_FOUND = 82;
    public static final int MAIL_ALREADY_CLAIMED = 83;

    // -------- 任务 --------
    public static final int QUEST_NOT_FOUND = 90;
    public static final int QUEST_ALREADY_ACCEPTED = 91;
    public static final int QUEST_NOT_COMPLETED = 92;
    public static final int QUEST_ALREADY_CLAIMED = 93;
    /** 前置任务未完成或条件引擎未通过。 */
    public static final int QUEST_LOCKED = 94;

    // -------- 匹配 --------
    public static final int MATCH_ALREADY_QUEUED = 100;
    public static final int MATCH_NOT_IN_QUEUE = 101;
    public static final int MATCH_LEVEL_NOT_ENOUGH = 102;

    // -------- 皮肤 --------
    public static final int SKIN_NOT_FOUND = 110;
    public static final int SKIN_NOT_OWNED = 111;
    /** 已拥有该皮肤（幂等成功或提示重复） */
    public static final int SKIN_ALREADY_OWNED = 112;
    /** 皮肤解锁道具无效（itemId 无法映射到皮肤） */
    public static final int SKIN_ITEM_INVALID = 113;

    // -------- 挑战 --------
    public static final int CHALLENGE_NOT_FOUND = 120;
    public static final int CHALLENGE_ALREADY_ACTIVE = 121;
    public static final int CHALLENGE_NOT_ACTIVE = 122;

    // -------- 抽卡 --------
    public static final int GACHA_BANNER_NOT_FOUND = 130;
    public static final int GACHA_INVALID_TIMES = 131;
    public static final int GACHA_CEILING_NOT_READY = 132;

    // -------- 肉鸽 --------
    public static final int ROGUE_NOT_ACTIVE = 140;
    public static final int ROGUE_ALREADY_ACTIVE = 141;
    public static final int ROGUE_INVALID_MOVE = 142;
    public static final int ROGUE_TALENT_POINTS_NOT_ENOUGH = 143;

    public static final int SHOP_PRODUCT_NOT_FOUND = 150;
    public static final int SHOP_PRODUCT_NOT_ON_SALE = 151;
    public static final int SHOP_DISCOUNT_EXPIRED = 152;
    public static final int SHOP_LIMIT_EXCEEDED = 153;
    public static final int SHOP_ORDER_NOT_FOUND = 154;
    public static final int SHOP_ORDER_INVALID_STATUS = 155;
    public static final int SHOP_FULFILL_FAILED = 156;
    public static final int SHOP_PAYMENT_VERIFY_FAILED = 157;
    public static final int SHOP_PAYMENT_AMOUNT_MISMATCH = 158;
    public static final int SHOP_REFUND_REJECTED = 159;
    public static final int SHOP_STOCK_EXCEEDED = 160;

    // -------- 大世界地貌 / 探索 --------
    /** 地形能量耗尽（弹射蘑菇/风场冷却中），不消耗体力 */
    public static final int TERRAIN_EXHAUSTED = 170;
    /** 客户端地形状态版本落后，需强制拉取最新 TerrainStateVector */
    public static final int TERRAIN_STATE_MISMATCH = 171;
    /** 访客试图交互房主限定资产（Host-Only） */
    public static final int HOST_ONLY_DENIED = 172;
    /** 联机剧情旁观中，移动输入被拒绝 */
    public static final int CUTSCENE_MOVE_BLOCKED = 173;
    /** 攀爬翻越成功 */
    public static final int VAULT_SUCCESS = 174;
    /** 动作状态与移动/战斗上下文不匹配 */
    public static final int ACTION_STATE_REJECTED = 175;
    /** 取消优先级不足，无法打断当前后摇 */
    public static final int CANCEL_PRIORITY_DENIED = 176;
    /** 钩锁预测 ACK（客户端可立即播放拉拽） */
    public static final int GRAPPLE_PREDICT_ACK = 177;

    // -------- 战斗 --------
    /** 战斗目标怪物在场景中不存在 */
    public static final int BATTLE_ENEMY_NOT_FOUND = 40; // findMonsterForBattle 返回空
    /** 玩家不在场景中，无法开战 */
    public static final int BATTLE_NOT_IN_SCENE = 41; // 场景状态与战斗请求不一致
    /** 战斗已结束，不可再提交操作 */
    public static final int BATTLE_ALREADY_ENDED = 42; // 对已结算 battleId 发操作
    /** 战斗操作类型或参数非法 */
    public static final int BATTLE_INVALID_ACTION = 43; // 回合、技能、目标等校验失败
    /** 战斗实例不存在 */
    public static final int BATTLE_NOT_FOUND = 44; // battleId 在 Redis/内存中无记录
    /** 阵容 lineupId 无效 */
    public static final int BATTLE_LINEUP_INVALID = 45; // 开战请求未带有效阵容
    /** 客户端上报的战斗结果与服务端不一致 */
    public static final int BATTLE_RESULT_MISMATCH = 46; // 防作弊：结算校验失败
    /** 玩家已有进行中的战斗 */
    public static final int BATTLE_ALREADY_ACTIVE = 47; // 不允许重复开战
    /** 战斗目标实体类型或状态无效 */
    public static final int BATTLE_TARGET_INVALID = 48; // 目标非怪物或不可攻击
    /** 战斗反作弊拒绝（伤害溢出 / 行为异常 / 封禁） */
    public static final int BATTLE_CHEAT_REJECTED = 49;

    // -------- 技能 --------
    /** 技能配置不存在 */
    public static final int SKILL_NOT_FOUND = 50; // skillId 在配置表无记录
    /** 技能已学习，不可重复学习 */
    public static final int SKILL_ALREADY_LEARNED = 51; // 学习接口幂等校验失败
    /** 角色等级不足，无法学习 */
    public static final int SKILL_LEVEL_NOT_ENOUGH = 52; // 学习前置等级未达标
    /** 技能冷却中 */
    public static final int SKILL_COOLDOWN = 53; // 释放时 CD 未结束
    /** 未学习该技能 */
    public static final int SKILL_NOT_LEARNED = 54; // 释放或查询未拥有的技能
    /** 法力/能量不足 */
    public static final int SKILL_MANA_NOT_ENOUGH = 55; // 消耗资源不够
    /** 被动技能不可主动释放 */
    public static final int SKILL_PASSIVE = 56; // 客户端误发主动释放被动技
    /** 技能目标无效 */
    public static final int SKILL_TARGET_INVALID = 57; // 目标实体不存在或类型错误
    /** 目标超出技能射程 */
    public static final int SKILL_OUT_OF_RANGE = 58; // 距离校验失败
    /** 不在场景中，无法释放场景技能 */
    public static final int SKILL_NOT_IN_SCENE = 59; // 与场景绑定的技能限制
    /** 技能释放被服务端拒绝（综合原因） */
    public static final int SKILL_CAST_REJECTED = 60; // 状态、Buff、沉默等导致无法施放

    // -------- Buff --------
    /** Buff 配置不存在 */
    public static final int BUFF_NOT_FOUND = 70; // buffTemplateId 无效
    /** 实体身上无该 Buff 实例 */
    public static final int BUFF_NOT_ON_ENTITY = 71; // 移除或查询时实例不存在
    /** 该 Buff 不可被主动移除 */
    public static final int BUFF_CANNOT_REMOVE = 72; // 配置为不可驱散
    /** 实体对请求方不可见（视野/线路隔离） */
    public static final int BUFF_ENTITY_NOT_VISIBLE = 73; // 跨实体查询 Buff 时权限/视野失败

    /**
     * 私有构造器，禁止实例化。
     */
    private RetCode() { // 仅通过类名访问常量
    }
}
