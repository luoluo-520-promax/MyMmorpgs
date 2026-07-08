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
