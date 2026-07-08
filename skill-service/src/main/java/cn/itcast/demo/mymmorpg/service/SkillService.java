/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/SkillService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：技能列表、习得、施放；冷却与当前法力存 Redis；施法需场景目标与距离校验。
 * 4) 变更建议：修改前先确认协议字段、Redis 键（skill:cdend:、player:mp:）与 Buff/MQ 上下游依赖。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 技能列表、习得、施放；冷却与当前法力存 Redis；施法需场景目标与距离校验

import cn.itcast.demo.mymmorpg.entity.Player; // JPA 玩家实体，习得时校验 level
import cn.itcast.demo.mymmorpg.entity.PlayerSkill; // player_skill 表：玩家已学技能
import cn.itcast.demo.mymmorpg.entity.PlayerSkillId; // 复合主键 (playerId, skillId)
import cn.itcast.demo.mymmorpg.entity.SkillConfig; // skill_config 表：静态技能配置
import cn.itcast.demo.mymmorpg.port.PlayerDataLoadPort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId; // GET_PLAYER_SKILLS_SC_RSP、SKILL_COOLDOWN_SC_NOTIFY 等
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage; // Handler 统一返回封装
import cn.itcast.demo.mymmorpg.protocol.RetCode; // 技能业务 retcode
import cn.itcast.demo.mymmorpg.protocol.protobuf.CastSkillCsReq; // 施法 CsReq（skillId、targetEntityId、坐标、时间戳）
import cn.itcast.demo.mymmorpg.protocol.protobuf.CastSkillScRsp; // 施法 ScRsp（伤害/治疗/剩余 CD）
import cn.itcast.demo.mymmorpg.protocol.protobuf.EffectInfo; // 施法附加效果（Buff 等）
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetPlayerSkillsCsReq; // 查技能列表 CsReq
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetPlayerSkillsScRsp; // 技能列表 ScRsp
import cn.itcast.demo.mymmorpg.protocol.protobuf.LearnSkillCsReq; // 习得技能 CsReq
import cn.itcast.demo.mymmorpg.protocol.protobuf.LearnSkillScRsp; // 习得 ScRsp
import cn.itcast.demo.mymmorpg.protocol.protobuf.PlayerSkillInfo; // 单个技能详情 Protobuf
import cn.itcast.demo.mymmorpg.protocol.protobuf.SkillCooldownScNotify; // 施法后推送剩余冷却（308）
import cn.itcast.demo.mymmorpg.protocol.protobuf.SkillLearnScNotify; // 服务端授予技能推送
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // player 表查等级
import cn.itcast.demo.mymmorpg.repository.PlayerSkillRepository; // player_skill 表 CRUD
import cn.itcast.demo.mymmorpg.service.BuffService; // 施法后对目标实体施加 Buff
import cn.itcast.demo.mymmorpg.support.SkillPolicy; // 伤害/治疗数值策略（等级、skillId 公式）
import org.springframework.beans.factory.annotation.Value; // 读取 game.player-preload.enabled
import org.springframework.data.redis.core.StringRedisTemplate; // skill:cdend:、player:mp: 键读写
import org.springframework.stereotype.Service; // 技能列表/习得/施法 Handler 与预加载 loadSkillsNow 共用
import org.springframework.transaction.annotation.Transactional; // 习得/授予技能写 player_skill 事务

import java.time.Duration; // Redis CD 键 TTL = cooldownSec + 120
import java.time.Instant; // CD 结束 epoch 秒、learnTime
import java.util.ArrayList; // 技能列表、EffectInfo 组装
import java.util.List; // Protobuf 列表参数
import java.util.Objects; // requireNonNull Redis 值

/**
 * 技能系统：skill_config + player_skill 持久化，冷却与当前 MP 放 Redis 减轻 DB 压力。
 * 主动施法需 SceneActorService 校验目标实体类型与同分线距离。
 */
@Service // skill_config+player_skill 持久化，CD/MP 存 Redis，施法经 SceneActorService 校验目标与距离
public class SkillService { // 技能系统：skill_config + player_skill 持久化，冷却与当前 MP 放 Redis 减轻 DB 压力

    /** skill_config.skillType：主动技能，可 CastSkill */
    public static final int SKILL_ACTIVE = 1; // skill_config.skillType：主动技能，可 CastSkill

    /** skill_config.skillType：被动技能，不可主动施放 */
    public static final int SKILL_PASSIVE = 2; // skill_config.skillType：被动技能，不可主动施放

    /** skill_config.targetType：敌对目标（怪物或玩家） */
    public static final int TARGET_ENEMY = 1; // skill_config.targetType：敌对目标（怪物或玩家）

    /** skill_config.targetType：友方玩家 */
    public static final int TARGET_ALLY = 2; // skill_config.targetType：友方玩家

    /** skill_config.targetType：自身 */
    public static final int TARGET_SELF = 3; // skill_config.targetType：自身

    /** Redis 冷却键前缀，完整键 skill:cdend:{playerId}:{skillId}，值为 CD 结束 epoch 秒 */
    private static final String REDIS_CD = "skill:cdend:"; // Redis 冷却键前缀，完整键 skill:cdend:{playerId}:{skillId}，值为 CD 结束 epoch 秒

    /** Redis 当前法力键前缀，完整键 player:mp:{playerId}，值为剩余 MP 整数 */
    private static final String REDIS_MP = "player:mp:"; // Redis 当前法力键前缀，完整键 player:mp:{playerId}，值为剩余 MP 整数

    /** 施法 CsReq 时间戳与服务器允许偏差（5 分钟），防重放 */
    private static final long TIMESTAMP_WINDOW_MS = 300_000L; // 施法 CsReq 时间戳与服务器允许偏差（5 分钟），防重放

    /** skill_config.range 为空或 0 时的默认施法距离（与场景坐标系一致，如像素） */
    private static final int DEFAULT_RANGE = 200; // skill_config.range 为空或 0 时的默认施法距离（与场景坐标系一致，如像素）

    /** 查 skill_config 静态配置 */
    private final ConfigQueryService configQueryService; // 查 skill_config 静态配置

    /** 查玩家等级，习得门槛校验 */
    private final PlayerRepository playerRepository; // 查玩家等级，习得门槛校验

    /** player_skill 表：已学技能、exists 校验 */
    private final PlayerSkillRepository playerSkillRepository; // player_skill 表：已学技能、exists 校验

    /** 施法目标距离、实体类型、是否在场景内 */
    private final SceneActorService sceneActorService; // 施法目标距离、实体类型、是否在场景内

    /** 读写 skill:cdend: 与 player:mp: */
    private final StringRedisTemplate stringRedisTemplate; // 读写 skill:cdend: 与 player:mp:

    /** 按等级与 skillId 计算 damage/heal */
    private final SkillPolicy skillPolicy; // 按等级与 skillId 计算 damage/heal

    /** 发布 skill.learned / skill.cast MQ 事件 */
    private final SkillEventPublisher skillEventPublisher; // 发布 skill.learned / skill.cast MQ 事件

    /** 推送 SKILL_COOLDOWN_SC_NOTIFY、SKILL_LEARN_SC_NOTIFY 及 DataReady 通知 */
    private final PlayerNotificationPort playerNotificationPort; // WebSocket 下行端口：施法 CD、授予技能时推 Notify 给客户端

    /** 施法 EffectInfo 含 buffId 时对目标 applyBuff */
    private final BuffService buffService; // Buff 服务：CastSkill 成功后对 targetEntityId 挂载 buffId 对应 Buff

    /** 预加载状态，GetPlayerSkills 未完成时返回 loading=true */
    private final PlayerDataLoadPort playerDataLoadPort; // 预加载状态端口：检查 DataType.SKILL 是否就绪，门控 handleGetPlayerSkills

    /** game.player-preload.enabled：true 时 GetPlayerSkills 等待 DataType.SKILL 就绪 */
    private final boolean preloadEnabled; // game.player-preload.enabled：true 时 GetPlayerSkills 等待 DataType.SKILL 就绪

    /**
     * 构造器注入配置、仓储、场景、Redis、策略、事件、推送、Buff 与预加载开关。
     */
    public SkillService( // 构造器注入配置、仓储、场景、Redis、策略、事件、推送、Buff 与预加载开关
            ConfigQueryService configQueryService, // 查 skill_config 静态属性
            PlayerRepository playerRepository, // 查 player.level 习得门槛
            PlayerSkillRepository playerSkillRepository, // player_skill 已学技能 CRUD
            SceneActorService sceneActorService, // 施法距离与目标类型校验
            StringRedisTemplate stringRedisTemplate, // skill:cdend: 与 player:mp: Redis
            SkillPolicy skillPolicy, // 伤害/治疗公式
            SkillEventPublisher skillEventPublisher, // skill.learned/cast MQ
            PlayerNotificationPort playerNotificationPort,
            BuffService buffService,
            PlayerDataLoadPort playerDataLoadPort,
            @Value("${game.player-preload.enabled:false}") boolean preloadEnabled) { // true 门控 GetPlayerSkills loading 占位
        this.configQueryService = configQueryService; // 持有技能配置查询
        this.playerRepository = playerRepository; // 持有 player 表访问
        this.playerSkillRepository = playerSkillRepository; // 持有已学技能仓储
        this.sceneActorService = sceneActorService; // 持有场景服务，施法范围校验
        this.stringRedisTemplate = stringRedisTemplate; // 持有 Redis，CD/MP 读写
        this.skillPolicy = skillPolicy; // 持有伤害/治疗策略
        this.skillEventPublisher = skillEventPublisher; // 持有技能事件发布器
        this.playerNotificationPort = playerNotificationPort; // 注入 WebSocket 推送端口，pushCooldownNotify/grantSkillAndNotify 使用
        this.buffService = buffService; // 注入 Buff 服务，CastSkill 成功后 applyBuff
        this.playerDataLoadPort = playerDataLoadPort; // 注入预加载状态端口，handleGetPlayerSkills 检查 SKILL 是否就绪
        this.preloadEnabled = preloadEnabled; // 记录预加载开关
    }

    /**
     * 客户端查技能列表：预加载未完成且 enabled 时返回 loading 占位，否则 loadSkillsNow。
     */
    public ProtocolMessage handleGetPlayerSkills(long playerId, GetPlayerSkillsCsReq req) { // 客户端查技能列表：预加载未完成且 enabled 时返回 loading 占位，否则 loadSkillsNow
        if (preloadEnabled && playerId > 0 // 预加载开启且已选角
                && !playerDataLoadPort.isReady(playerId, PlayerDataLoadPort.DataType.SKILL)) { // DataType.SKILL 未就绪
            return listLoadingRsp(); // GET_PLAYER_SKILLS_SC_RSP loading=true
        }
        return loadSkillsNow(playerId); // 同步加载完整技能列表
    }

    /**
     * 强制同步加载技能列表：查 player_skill + skill_config，组装 PlayerSkillInfo（含 Redis 剩余 CD）。
     * 供 Handler 与 PlayerDataAsyncPreloadService.preloadSkill 共用。
     */
    public ProtocolMessage loadSkillsNow(long playerId) { // 强制同步加载技能列表：查 player_skill + skill_config，组装 PlayerSkillInfo（含 Redis 剩余 CD）
        if (playerId <= 0) { // 未选角
            return listRsp(RetCode.PLAYER_NOT_SELECTED, List.of()); // GET_PLAYER_SKILLS_SC_RSP 拒绝
        }
        List<PlayerSkill> rows = playerSkillRepository.findByIdPlayerIdOrderByLearnTimeAsc(playerId); // 按 learnTime 查 player_skill
        List<PlayerSkillInfo> out = new ArrayList<>(rows.size()); // 技能 Protobuf 收集器
        for (PlayerSkill ps : rows) { // 逐条已学技能
            SkillConfig sc = configQueryService.findSkillById(ps.getId().getSkillId()); // 关联 skill_config
            if (sc == null) { // 配置已删
                continue; // 跳过脏数据
            }
            out.add(buildSkillInfo(sc, ps.getLearnTime(), playerId)); // 合并配置+Redis CD
        }
        return listRsp(RetCode.OK, out); // GET_PLAYER_SKILLS_SC_RSP 完整列表
    }

    /**
     * 习得技能：校验配置、未重复、玩家等级 -> 写 player_skill -> 发布 MQ 事件。
     */
    @Transactional // INSERT player_skill 需事务保证习得原子性
    public ProtocolMessage handleLearnSkill(long playerId, LearnSkillCsReq req) { // 习得技能：校验配置、未重复、玩家等级 -> 写 player_skill -> 发布 MQ 事件
        if (playerId <= 0) { // 未选角
            return learnRsp(RetCode.PLAYER_NOT_SELECTED, 0, null); // LEARN_SKILL_SC_RSP 拒绝
        }
        int sid = req.getSkillId(); // 请求习得的 skillId
        SkillConfig sc = configQueryService.findSkillById(sid); // 查 skill_config
        if (sc == null) { // 技能不存在
            return learnRsp(RetCode.SKILL_NOT_FOUND, sid, null); // SKILL_NOT_FOUND
        }
        if (playerSkillRepository.existsByIdPlayerIdAndIdSkillId(playerId, sid)) { // 已学过
            return learnRsp(RetCode.SKILL_ALREADY_LEARNED, sid, null); // SKILL_ALREADY_LEARNED
        }
        var pOpt = playerRepository.findById(playerId); // 查 player.level
        if (pOpt.isEmpty()) { // 角色不存在
            return learnRsp(RetCode.PLAYER_NOT_FOUND, sid, null); // PLAYER_NOT_FOUND
        }
        Player p = pOpt.get(); // 玩家实体
        int lv = p.getLevel() == null ? 1 : p.getLevel(); // 当前等级
        if (lv < (sc.getNeedLevel() == null ? 0 : sc.getNeedLevel())) { // 未达 skill_config.need_level
            return learnRsp(RetCode.SKILL_LEVEL_NOT_ENOUGH, sid, null); // SKILL_LEVEL_NOT_ENOUGH
        }
        Instant now = Instant.now(); // 习得时间戳
        PlayerSkill ps = new PlayerSkill(); // 新建 player_skill 行实体
        ps.setId(new PlayerSkillId(playerId, sid)); // 复合主键 (playerId, skillId)
        ps.setLearnTime(now); // 记录 learnTime
        playerSkillRepository.save(ps); // INSERT player_skill

        skillEventPublisher.publishSkillLearned(playerId, sid); // MQ 上报 skill.learned
        PlayerSkillInfo info = buildSkillInfo(sc, now, playerId); // 组装习得后技能详情
        return learnRsp(RetCode.OK, sid, info); // LEARN_SKILL_SC_RSP 成功
    }

    /**
     * 施放主动技能：时间戳、已学、非被动、CD、MP -> 场景目标校验 -> 扣 MP、写 CD -> 算伤/疗 -> Buff -> 推送 CD Notify。
     */
    public ProtocolMessage handleCastSkill(long playerId, CastSkillCsReq req) { // 施放主动技能：时间戳、已学、非被动、CD、MP -> 场景目标校验 -> 扣 MP、写 CD -> 算伤/疗 -> Buff -> 推送 CD Notify
        if (playerId <= 0) { // 未选角
            return castRsp(RetCode.PLAYER_NOT_SELECTED, req.getSkillId(), 0, 0, 0, List.of()); // CAST_SKILL_SC_RSP 拒绝
        }
        long nowMs = System.currentTimeMillis(); // 服务端毫秒时间戳
        if (Math.abs(nowMs - req.getTimestamp()) > TIMESTAMP_WINDOW_MS) { // 客户端时钟偏差超 5 分钟
            return castRsp(RetCode.SKILL_CAST_REJECTED, req.getSkillId(), 0, 0, 0, List.of()); // 防重放，SKILL_CAST_REJECTED
        }
        int sid = req.getSkillId(); // 施法 skillId
        SkillConfig sc = configQueryService.findSkillById(sid); // 查 skill_config
        if (sc == null) { // 技能不存在
            return castRsp(RetCode.SKILL_NOT_FOUND, sid, 0, 0, 0, List.of()); // SKILL_NOT_FOUND
        }
        if (!playerSkillRepository.existsByIdPlayerIdAndIdSkillId(playerId, sid)) { // 未习得
            return castRsp(RetCode.SKILL_NOT_LEARNED, sid, 0, 0, 0, List.of()); // SKILL_NOT_LEARNED
        }
        int st = sc.getSkillType() == null ? SKILL_ACTIVE : sc.getSkillType(); // skillType
        if (st == SKILL_PASSIVE) { // 被动技能
            return castRsp(RetCode.SKILL_PASSIVE, sid, 0, 0, 0, List.of()); // SKILL_PASSIVE 不可主动施放
        }
        int cdRem = getRemainingCooldown(playerId, sid); // 读 skill:cdend:{playerId}:{skillId} 剩余 CD
        if (cdRem > 0) { // 仍在冷却
            return castRsp(RetCode.SKILL_COOLDOWN, sid, cdRem, 0, 0, List.of()); // SKILL_COOLDOWN 含剩余秒数
        }
        var pOpt = playerRepository.findById(playerId); // 查 player.level 算 MP 上限
        if (pOpt.isEmpty()) { // 角色不存在
            return castRsp(RetCode.PLAYER_NOT_FOUND, sid, 0, 0, 0, List.of()); // PLAYER_NOT_FOUND
        }
        Player p = pOpt.get(); // 玩家实体
        int plv = p.getLevel() == null ? 1 : p.getLevel(); // 等级
        int manaCost = sc.getManaCost() == null ? 0 : sc.getManaCost(); // skill_config.mana_cost
        int curMp = ensureMp(playerId, plv); // 读/初始化 player:mp:{playerId}
        if (curMp < manaCost) { // MP 不足
            return castRsp(RetCode.SKILL_MANA_NOT_ENOUGH, sid, 0, 0, 0, List.of()); // SKILL_MANA_NOT_ENOUGH
        }

        int targetCode = validateCastTarget(playerId, sc, req); // 场景内目标类型与距离校验
        if (targetCode != RetCode.OK) { // 目标非法或超 range
            return castRsp(targetCode, sid, 0, 0, 0, List.of()); // 目标校验失败：可能是 SKILL_TARGET_NOT_FOUND / SKILL_OUT_OF_RANGE 等 retcode
        }

        consumeMp(playerId, manaCost, plv); // 扣 MP 写 player:mp:{playerId}
        int cooldownSec = sc.getCooldown() == null ? 0 : sc.getCooldown(); // skill_config.cooldown 秒
        if (cooldownSec > 0) { // 有 CD
            setCooldownEnd(playerId, sid, cooldownSec); // 写 skill:cdend:{playerId}:{skillId}
        }

        int damage = 0; // 伤害值（敌对技能）
        int heal = 0; // 治疗值（友方/自身）
        int tt = sc.getTargetType() == null ? TARGET_ENEMY : sc.getTargetType(); // targetType
        if (tt == TARGET_ALLY || tt == TARGET_SELF) { // 治疗类目标
            heal = skillPolicy.computeHeal(plv, sid); // SkillPolicy 算治疗量
        } else { // 伤害类目标
            damage = skillPolicy.computeDamage(plv, sid, tt); // SkillPolicy 算伤害量
        }

        List<EffectInfo> effects = new ArrayList<>(); // 施法附加效果（Buff 等）
        if (damage > 0 && req.getTargetEntityId() != 0) { // 有伤害且指定 entity 目标
            effects.add(EffectInfo.newBuilder() // 伤害技能附加 Buff 效果 Protobuf
                    .setEffectType(1) // effectType=1 表示 Buff 附加
                    .setTargetEntityId(req.getTargetEntityId()) // 目标 entityId
                    .setBuffId(1) // 示例 buffId=1
                    .build()); // 完成 EffectInfo，施法后 applyBuff
        }

        skillEventPublisher.publishSkillCast(playerId, sid, req.getTargetEntityId(), damage, heal); // MQ 上报 skill.cast

        if (cooldownSec > 0) { // 有 CD 需推送
            long startSec = Instant.now().getEpochSecond(); // CD 开始 epoch 秒
            pushCooldownNotify(playerId, sid, cooldownSec, startSec); // SKILL_COOLDOWN_SC_NOTIFY(308)
        }

        for (EffectInfo effect : effects) { // 遍历施法附加 EffectInfo（Buff 等）
            if (effect.getBuffId() > 0 && effect.getTargetEntityId() != 0L) { // 有效 Buff 目标
                buffService.applyBuff(effect.getTargetEntityId(), effect.getBuffId()); // 对目标实体挂 Buff
            }
        }

        return castRsp(RetCode.OK, sid, cooldownSec, damage, heal, effects); // CAST_SKILL_SC_RSP 成功
    }

    /**
     * 任务或 GM 授予技能：落库 player_skill 并推送 SKILL_LEARN_SC_NOTIFY，已存在则跳过。
     */
    @Transactional // INSERT player_skill 需事务保证 GM/任务授予技能原子性
    public void grantSkillAndNotify(long playerId, int skillId) { // 任务或 GM 授予技能：落库 player_skill 并推送 SKILL_LEARN_SC_NOTIFY，已存在则跳过
        SkillConfig sc = configQueryService.findSkillById(skillId); // 查 skill_config
        if (sc == null || playerSkillRepository.existsByIdPlayerIdAndIdSkillId(playerId, skillId)) { // 无效或已拥有
            return; // 静默跳过
        }
        Instant now = Instant.now(); // 授予时间
        PlayerSkill ps = new PlayerSkill(); // 新建 player_skill 行
        ps.setId(new PlayerSkillId(playerId, skillId)); // 复合主键
        ps.setLearnTime(now); // learnTime
        playerSkillRepository.save(ps); // INSERT player_skill
        skillEventPublisher.publishSkillLearned(playerId, skillId); // MQ 上报
        PlayerSkillInfo info = buildSkillInfo(sc, now, playerId); // 技能详情
        var n = SkillLearnScNotify.newBuilder().setSkillInfo(info).build(); // SKILL_LEARN_SC_NOTIFY 体
        playerNotificationPort.send(playerId, MessageId.SKILL_LEARN_SC_NOTIFY, n.toByteArray()); // 推送新技能给客户端
    }

    /** 推送 SKILL_COOLDOWN_SC_NOTIFY(308)，客户端更新技能栏 CD 圈 */
    private void pushCooldownNotify(long playerId, int skillId, int remainingSec, long startEpochSec) { // 推送 SKILL_COOLDOWN_SC_NOTIFY(308)，客户端更新技能栏 CD 圈
        var n = SkillCooldownScNotify.newBuilder() // CD 推送 Protobuf
                .setSkillId(skillId) // 进入 CD 的 skillId
                .setRemainingCooldown(remainingSec) // 剩余冷却秒数
                .setCooldownStartTime(startEpochSec) // CD 开始 epoch 秒
                .build(); // 完成组装
        playerNotificationPort.send(playerId, MessageId.SKILL_COOLDOWN_SC_NOTIFY, n.toByteArray()); // msgId=308
    }

    /**
     * 施法目标校验：SELF 仅允许自身；ENEMY 需场景内怪物/玩家且距离 <= range；ALLY 需同线玩家。
     */
    private int validateCastTarget(long playerId, SkillConfig sk, CastSkillCsReq req) { // 施法目标校验：SELF 仅允许自身；ENEMY 需场景内怪物/玩家且距离 <= range；ALLY 需同线玩家
        int tt = sk.getTargetType() == null ? TARGET_ENEMY : sk.getTargetType(); // targetType
        long tid = req.getTargetEntityId(); // 目标 entityId
        boolean hasPos = req.getTargetX() != 0f || req.getTargetY() != 0f || req.getTargetZ() != 0f; // 是否指定地面坐标
        int range = sk.getRange() == null || sk.getRange() == 0 ? DEFAULT_RANGE : sk.getRange(); // 施法距离，默认 200

        if (tt == TARGET_SELF) { // 自身目标技能
            if (tid != 0 && tid != playerId) { // 指定了他人 entityId
                return RetCode.SKILL_TARGET_INVALID; // 自身技能目标非法
            }
            return RetCode.OK; // 自身技能通过
        }
        if (!sceneActorService.isPlayerInScene(playerId)) { // 施法者不在场景
            return RetCode.SKILL_NOT_IN_SCENE; // 需进场景才能对目标施法
        }
        if (tt == TARGET_ENEMY) { // 敌对目标
            if (tid != 0) { // 指定 entity 目标
                if (tid == playerId) { // 不能以自己为敌
                    return RetCode.SKILL_TARGET_INVALID; // 目标非法
                }
                var tType = sceneActorService.getEntityTypeInLine(playerId, tid); // 同分线查 entityType
                if (tType.isEmpty()) { // 目标不在同线
                    return RetCode.SKILL_TARGET_INVALID; // 目标不可见
                }
                int et = tType.get(); // ENTITY_PLAYER 或 MONSTER
                if (et != SceneActorService.ENTITY_MONSTER && et != SceneActorService.ENTITY_PLAYER) { // 非可攻击类型
                    return RetCode.SKILL_TARGET_INVALID; // 不能对 NPC 等施敌对技能
                }
                var dist = sceneActorService.distanceBetweenEntities(playerId, tid); // 同线三维距离
                if (dist.isEmpty() || dist.get() > range) { // 超 skill_config.range
                    return RetCode.SKILL_OUT_OF_RANGE; // 超出施法距离
                }
                return RetCode.OK; // 敌对 entity 目标通过
            }
            if (hasPos) { // 地面坐标 AOE
                var dist = sceneActorService.distancePlayerToPoint(playerId, req.getTargetX(), req.getTargetY(), req.getTargetZ()); // 到目标点距离
                if (dist.isEmpty() || dist.get() > range) { // 超 range
                    return RetCode.SKILL_OUT_OF_RANGE; // 地面点过远
                }
                return RetCode.OK; // 地面目标通过
            }
            return RetCode.SKILL_TARGET_INVALID; // 未指定 entity 也未指定坐标
        }
        if (tt == TARGET_ALLY) { // 友方目标
            if (tid == 0 || tid == playerId) { // 必须指定其他玩家
                return RetCode.SKILL_TARGET_INVALID; // 友方目标非法
            }
            var tType = sceneActorService.getEntityTypeInLine(playerId, tid); // 同线查类型
            if (tType.isEmpty() || tType.get() != SceneActorService.ENTITY_PLAYER) { // 非同线玩家
                return RetCode.SKILL_TARGET_INVALID; // 只能对友方玩家
            }
            var dist = sceneActorService.distanceBetweenEntities(playerId, tid); // 距离校验
            if (dist.isEmpty() || dist.get() > range) { // 超 range
                return RetCode.SKILL_OUT_OF_RANGE; // 友方过远
            }
            return RetCode.OK; // 友方目标通过
        }
        return RetCode.SKILL_TARGET_INVALID; // 未知 targetType
    }

    /** 组装 PlayerSkillInfo：合并 skill_config 与 Redis 剩余 CD、learnTime epoch 秒 */
    private PlayerSkillInfo buildSkillInfo(SkillConfig sc, Instant learnTime, long playerId) { // 组装 PlayerSkillInfo：合并 skill_config 与 Redis 剩余 CD、learnTime epoch 秒
        int sid = sc.getId(); // skillId
        float ct = sc.getCastTime() == null ? 0f : sc.getCastTime().floatValue(); // 读条时间 castTime
        int rem = getRemainingCooldown(playerId, sid); // Redis 剩余 CD 秒
        String shapeJson = sc.getShapeParams() == null ? "" : sc.getShapeParams(); // 范围形状 JSON
        String effect = sc.getEffect() == null ? "" : sc.getEffect(); // 效果描述 JSON
        return PlayerSkillInfo.newBuilder() // 组装 PlayerSkillInfo：合并 skill_config 静态字段与 Redis 剩余 CD
                .setSkillId(sid) // skillId
                .setSkillName(sc.getName() == null ? "" : sc.getName()) // 技能名
                .setSkillEffect(effect) // 效果 JSON
                .setNeedLevel(sc.getNeedLevel() == null ? 0 : sc.getNeedLevel()) // 习得等级
                .setCooldown(sc.getCooldown() == null ? 0 : sc.getCooldown()) // 冷却秒
                .setManaCost(sc.getManaCost() == null ? 0 : sc.getManaCost()) // 耗 MP
                .setCastTime(ct) // 读条
                .setSkillType(sc.getSkillType() == null ? SKILL_ACTIVE : sc.getSkillType()) // 主动/被动
                .setTargetType(sc.getTargetType() == null ? TARGET_ENEMY : sc.getTargetType()) // 目标类型
                .setRange(sc.getRange() == null ? 0 : sc.getRange()) // 施法距离
                .setShape(sc.getShape() == null ? 1 : sc.getShape()) // 范围形状
                .setShapeParams(shapeJson) // 形状参数
                .setRemainingCooldown(rem) // 当前剩余 CD
                .setLearnTime(learnTime.getEpochSecond()) // 习得 epoch 秒
                .build(); // 完成 PlayerSkillInfo
    }

    /** 读 Redis skill:cdend:{playerId}:{skillId}，计算剩余冷却秒数 */
    private int getRemainingCooldown(long playerId, int skillId) { // 读 Redis skill:cdend:{playerId}:{skillId}，计算剩余冷却秒数
        String v = stringRedisTemplate.opsForValue().get(REDIS_CD + playerId + ":" + skillId); // GET CD 结束 epoch 秒
        if (v == null) { // 无 CD 键
            return 0; // 无冷却
        }
        long end = Long.parseLong(v); // CD 结束 epoch 秒
        long now = Instant.now().getEpochSecond(); // 当前 epoch 秒
        return (int) Math.max(0, end - now); // 剩余 CD = max(0, end-now)
    }

    /** 写 CD 结束 epoch 秒到 Redis，TTL = cooldownSec + 120 自动过期 */
    private void setCooldownEnd(long playerId, int skillId, int cooldownSec) { // 写 CD 结束 epoch 秒到 Redis，TTL = cooldownSec + 120 自动过期
        long end = Instant.now().getEpochSecond() + cooldownSec; // CD 结束 = 现在 + cooldownSec
        stringRedisTemplate.opsForValue().set( // 写 skill:cdend:{playerId}:{skillId} CD 结束 epoch 秒
                REDIS_CD + playerId + ":" + skillId, // skill:cdend:{playerId}:{skillId}
                Objects.requireNonNull(String.valueOf(end)), // 值=结束 epoch 秒
                Objects.requireNonNull(Duration.ofSeconds(cooldownSec + 120L))); // TTL 略长于 CD 防键残留
    }

    /** MP 上限公式：100 + level * 10 */
    private int maxMp(int level) { // MP 上限公式：100 + level * 10
        int lv = Math.max(1, level); // 等级至少 1
        return 100 + lv * 10; // 100 + level*10
    }

    /** 确保 player:mp:{playerId} 存在，无键时用 maxMp(level) 初始化 */
    private int ensureMp(long playerId, int level) { // 确保 player:mp:{playerId} 存在，无键时用 maxMp(level) 初始化
        String k = REDIS_MP + playerId; // player:mp:{playerId}
        String v = stringRedisTemplate.opsForValue().get(k); // GET 当前 MP
        int max = maxMp(level); // 按等级算 MP 上限
        if (v == null) { // 首次施法/查 MP
            stringRedisTemplate.opsForValue().set(k, Objects.requireNonNull(String.valueOf(max))); // 初始化为满 MP
            return max; // Redis 无 MP 键时按等级上限初始化，供本次施法扣蓝基准
        }
        return Integer.parseInt(v); // 读取 player:mp:{playerId} 中上次战斗/施法后的剩余法力值
    }

    /** 施法扣 MP：ensureMp 后写入扣减结果到 player:mp:{playerId} */
    private void consumeMp(long playerId, int cost, int level) { // 施法扣 MP：ensureMp 后写入扣减结果到 player:mp:{playerId}
        int cur = ensureMp(playerId, level); // 确保 MP 键存在
        int next = Math.max(0, cur - cost); // 扣减后 MP，不低于 0
        stringRedisTemplate.opsForValue().set(REDIS_MP + playerId, Objects.requireNonNull(String.valueOf(next))); // 写回 player:mp
    }

    /** 组装 GET_PLAYER_SKILLS_SC_RSP，loading=false */
    private static ProtocolMessage listRsp(int code, List<PlayerSkillInfo> skills) { // 组装 GET_PLAYER_SKILLS_SC_RSP，loading=false
        var b = GetPlayerSkillsScRsp.newBuilder().setRetcode(code); // 技能列表响应
        b.setLoading(false); // 完整数据
        if (code == RetCode.OK) { // 成功才填 skills
            b.addAllSkills(skills); // 全部 PlayerSkillInfo
        }
        return new ProtocolMessage(MessageId.GET_PLAYER_SKILLS_SC_RSP, b.build().toByteArray()); // msgId=GET_PLAYER_SKILLS_SC_RSP
    }

    /** 预加载未完成时的占位响应：retcode=OK、loading=true，客户端可等待 DataReady Notify 或轮询 */
    private static ProtocolMessage listLoadingRsp() { // 预加载未完成时的占位响应：retcode=OK、loading=true，客户端可等待 DataReady Notify 或轮询
        var b = GetPlayerSkillsScRsp.newBuilder() // 预加载占位：retcode=OK loading=true
                .setRetcode(RetCode.OK) // retcode OK
                .setLoading(true); // loading=true 等待 DataType.SKILL 就绪
        return new ProtocolMessage(MessageId.GET_PLAYER_SKILLS_SC_RSP, b.build().toByteArray()); // msgId=GET_PLAYER_SKILLS_SC_RSP
    }

    /** 组装 LEARN_SKILL_SC_RSP */
    private static ProtocolMessage learnRsp(int code, int skillId, PlayerSkillInfo info) { // 组装 LEARN_SKILL_SC_RSP
        var b = LearnSkillScRsp.newBuilder().setRetcode(code).setSkillId(skillId); // 习得响应
        if (code == RetCode.OK && info != null) { // 成功才填 skillInfo
            b.setSkillInfo(info); // 新习得技能详情
        }
        return new ProtocolMessage(MessageId.LEARN_SKILL_SC_RSP, b.build().toByteArray()); // msgId=LEARN_SKILL_SC_RSP
    }

    /** 组装 CAST_SKILL_SC_RSP，含剩余 CD、伤害、治疗、EffectInfo 列表 */
    private static ProtocolMessage castRsp( // 组装 CAST_SKILL_SC_RSP，含剩余 CD、伤害、治疗、EffectInfo 列表
            int code, int skillId, int remainingCd, int damage, int heal, List<EffectInfo> effects) { // CAST_SKILL_SC_RSP 参数
        var b = CastSkillScRsp.newBuilder().setRetcode(code).setSkillId(skillId); // 施法响应
        if (remainingCd > 0) { // 有剩余 CD
            b.setRemainingCooldown(remainingCd); // 剩余冷却秒
        }
        if (damage > 0) { // 有伤害
            b.setDamage(damage); // 伤害值
        }
        if (heal > 0) { // 有治疗
            b.setHeal(heal); // 治疗值
        }
        if (effects != null && !effects.isEmpty()) { // 有附加效果
            b.addAllEffects(effects); // EffectInfo 列表
        }
        return new ProtocolMessage(MessageId.CAST_SKILL_SC_RSP, b.build().toByteArray()); // msgId=CAST_SKILL_SC_RSP
    }
}
