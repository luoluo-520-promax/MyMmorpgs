/**
 * 文件说明：战斗核心业务服务类。
 * 职责：承载状态同步战斗的完整流程——开战、回合行动、结算；怪物属性来自 monster_config，
 *       运行时状态持久化到 Redis，结算通过 RocketMQ 事件发布。
 * 注意：修改前请确认上下游依赖、协议字段与缓存键是否受影响。
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.anticheat.AntiCheatService;
import cn.itcast.demo.mymmorpg.challenge.ChallengeConfigDocument;
import cn.itcast.demo.mymmorpg.challenge.ChallengeConfigLoader;
import cn.itcast.demo.mymmorpg.element.ElementReactionEngine;
import cn.itcast.demo.mymmorpg.element.ElementType;
import cn.itcast.demo.mymmorpg.element.ReactionResult;
import cn.itcast.demo.mymmorpg.model.BattleSceneFactory;
import cn.itcast.demo.mymmorpg.model.admin.BattleStatsSnapshot;
import cn.itcast.demo.mymmorpg.entity.MonsterConfig;
import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.entity.SkillConfig;
import cn.itcast.demo.mymmorpg.support.BattlePolicy;
import cn.itcast.demo.mymmorpg.tlog.TLogEventPublisher;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleActionScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEndScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleEntityInfo;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleStartScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.BattleSyncScNotify;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EnterEncounterScNotify;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EntityUpdate;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ExitEncounterScNotify;
import cn.itcast.demo.mymmorpg.protocol.protobuf.ItemReward;
import cn.itcast.demo.mymmorpg.protocol.protobuf.TurnInfo;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository;
import cn.itcast.demo.mymmorpg.port.BattleScenePort;
import cn.itcast.demo.mymmorpg.port.PlayerNotificationPort;
import cn.itcast.demo.mymmorpg.port.PlayerProgressPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 状态同步战斗：怪物属性来自 {@code monster_config}，运行时状态存 Redis、结算走 RocketMQ 事件。
 */
@Service // 注册为 Spring 单例服务，可被注入到其他组件
public class BattleService { // 战斗核心业务服务类

    /** Redis 键前缀：存储单场战斗完整状态 */
    private static final String REDIS_STATE = "battle:state:"; // Redis 键前缀：战斗状态
    /** Redis 键前缀：玩家当前进行中的战斗 ID */
    private static final String REDIS_ACTIVE = "battle:active:"; // Redis 键前缀：活跃战斗绑定
    /** Redis SET：活跃战斗玩家 ID 索引（替代 KEYS 扫描） */
    private static final String REDIS_ACTIVE_INDEX = "battle:active:index";
    /** 战斗相关 Redis 键的过期时间：30 分钟 */
    private static final Duration STATE_TTL = Duration.ofMinutes(30); // 状态 TTL
    /** 客户端时间戳允许偏差窗口：5 分钟，防重放 */
    private static final long TIMESTAMP_WINDOW_MS = 300_000L; // 时间戳校验窗口

    /** 同步类型：属性变更（血量、蓝量等） */
    public static final int SYNC_ATTR = 1; // 同步类型：属性
    /** 同步类型：战斗已结束 */
    public static final int SYNC_BATTLE_END = 4; // 同步类型：战斗结束

    /** 行动类型：普通攻击 */
    private static final int ACTION_NORMAL = 1; // 普通攻击
    /** 行动类型：技能 */
    private static final int ACTION_SKILL = 2; // 技能攻击
    /** 行动类型：使用道具 */
    private static final int ACTION_ITEM = 3; // 使用道具

    /** 回合时限（秒），填充 TurnInfo */
    private static final int TURN_TIME_LIMIT_SEC = 30;

    private final ConfigQueryService configQueryService;
    private final PlayerRepository playerRepository;
    private final BattleScenePort battleScenePort;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final BattlePolicy battlePolicy;
    private final BattleEventPublisher battleEventPublisher;
    private final PlayerNotificationPort playerNotificationPort;
    private final PlayerProgressPort playerProgressPort;
    private final BattleSceneFactory battleSceneFactory;
    private final BattleStatsCollector battleStatsCollector;
    private final ChallengeConfigLoader challengeConfigLoader;
    private final ObjectProvider<RogueService> rogueServiceProvider;
    private final ObjectProvider<BattleEndProjectionNotifier> battleEndProjectionNotifier;
    private final ElementReactionEngine elementReactionEngine;
    private final ObjectProvider<TLogEventPublisher> tLogEventPublisher;
    private final AntiCheatService antiCheatService;

    /** 战斗 ID 自增序列，起始值 10000 */
    private final AtomicLong battleIdSeq = new AtomicLong(10_000L); // 战斗 ID 序列

    /**
     * JMX：已分配的最大战斗 ID（单调递增）。
     *
     * @return 当前序列值
     */
    public long getIssuedBattleIdMax() { // 供监控暴露：当前已发出的最大 battleId
        return battleIdSeq.get(); // 读取原子变量当前值（不递增）
    }

    /**
     * JMX：当前 Redis 中玩家进行中的战斗绑定数量。
     *
     * @return 活跃绑定数
     */
    public int countActiveBattleBindings() { // 供监控暴露：有多少玩家正处于战斗
        Long size = stringRedisTemplate.opsForSet().size(REDIS_ACTIVE_INDEX); // O(1) SCARD，避免 KEYS
        int active = size == null ? 0 : size.intValue();
        battleStatsCollector.observeActiveBindings(active);
        return active;
    }

    /** 只读统计快照（含胜率、时长、技能使用率、按怪模板聚合）。 */
    public BattleStatsSnapshot statsSnapshot() {
        return battleStatsCollector.snapshot(getIssuedBattleIdMax(), countActiveBattleBindings());
    }

    public cn.itcast.demo.mymmorpg.model.ai.PlayerBattleLite playerBattleStats(long playerId) {
        return battleStatsCollector.playerStats(playerId);
    }

    /**
     * 构造器注入所有依赖。
     */
    public BattleService(
            ConfigQueryService configQueryService,
            PlayerRepository playerRepository,
            BattleScenePort battleScenePort,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            BattlePolicy battlePolicy,
            BattleEventPublisher battleEventPublisher,
            PlayerNotificationPort playerNotificationPort,
            PlayerProgressPort playerProgressPort,
            BattleSceneFactory battleSceneFactory,
            BattleStatsCollector battleStatsCollector,
            ChallengeConfigLoader challengeConfigLoader,
            ObjectProvider<RogueService> rogueServiceProvider,
            ObjectProvider<BattleEndProjectionNotifier> battleEndProjectionNotifier,
            ElementReactionEngine elementReactionEngine,
            ObjectProvider<TLogEventPublisher> tLogEventPublisher,
            ObjectProvider<AntiCheatService> antiCheatServiceProvider) {
        this.configQueryService = configQueryService;
        this.playerRepository = playerRepository;
        this.battleScenePort = battleScenePort;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.battlePolicy = battlePolicy;
        this.battleEventPublisher = battleEventPublisher;
        this.playerNotificationPort = playerNotificationPort;
        this.playerProgressPort = playerProgressPort;
        this.battleSceneFactory = battleSceneFactory;
        this.battleStatsCollector = battleStatsCollector;
        this.challengeConfigLoader = challengeConfigLoader;
        this.rogueServiceProvider = rogueServiceProvider;
        this.battleEndProjectionNotifier = battleEndProjectionNotifier;
        this.elementReactionEngine = elementReactionEngine == null
                ? new ElementReactionEngine() : elementReactionEngine;
        this.tLogEventPublisher = tLogEventPublisher;
        AntiCheatService injected = antiCheatServiceProvider == null
                ? null : antiCheatServiceProvider.getIfAvailable();
        this.antiCheatService = injected == null ? new AntiCheatService() : injected;
    }

    /**
     * 处理「开始战斗」请求。
     *
     * @param playerId 玩家 ID
     * @param req      开始战斗请求
     * @return 协议响应消息
     */
    public ProtocolMessage handleBattleStart(long playerId, BattleStartCsReq req) {
        if (playerId <= 0) {
            return startRsp(RetCode.PLAYER_NOT_SELECTED, StartPayload.empty());
        }
        if (req.getLineupId() == 0) {
            return startRsp(RetCode.BATTLE_LINEUP_INVALID, StartPayload.empty());
        }
        long enemyEntityId = req.getSceneEntityId() > 0
                ? req.getSceneEntityId()
                : Integer.toUnsignedLong(req.getEnemyId());
        var lockOpt = battleScenePort.markMonsterInCombat(playerId, enemyEntityId);
        if (lockOpt.isEmpty()) {
            return startRsp(RetCode.BATTLE_ENEMY_NOT_FOUND, StartPayload.empty());
        }
        var lock = lockOpt.get();
        MonsterConfig mc = configQueryService.findMonsterById(lock.monsterTemplateId());
        if (mc == null) {
            battleScenePort.releaseMonsterFromCombat(playerId, enemyEntityId);
            return startRsp(RetCode.BATTLE_ENEMY_NOT_FOUND, StartPayload.empty());
        }
        var playerOpt = playerRepository.findById(playerId);
        if (playerOpt.isEmpty()) {
            battleScenePort.releaseMonsterFromCombat(playerId, enemyEntityId);
            return startRsp(RetCode.PLAYER_NOT_FOUND, StartPayload.empty());
        }
        Player player = playerOpt.get();
        String activeKey = REDIS_ACTIVE + playerId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(activeKey))) {
            battleScenePort.releaseMonsterFromCombat(playerId, enemyEntityId);
            return startRsp(RetCode.BATTLE_ALREADY_ACTIVE, StartPayload.empty());
        }

        int[] ps = playerCombatStats(player.getLevel() == null ? 1 : player.getLevel());
        long battleId = battleIdSeq.incrementAndGet();
        var state = battleSceneFactory.createState(
                battleId,
                lock.sceneId(),
                playerId,
                Integer.toUnsignedLong(req.getLineupId()),
                enemyEntityId,
                mc,
                player,
                mc.getName(),
                mc.getLevel() == null ? 1 : mc.getLevel(),
                mc.getExpReward() == null ? 0 : mc.getExpReward(),
                req.getBattleType()
        );
        state.playerHpMax = ps[0];
        state.playerHp = ps[0];
        state.playerMpMax = ps[1];
        state.playerMp = ps[1];
        state.playerAttack = ps[2];
        state.playerDefense = ps[3];
        state.enemyHpMax = mc.getHpMax() == null ? 100 : mc.getHpMax();
        state.enemyHp = state.enemyHpMax;
        state.enemyMpMax = mc.getMpMax() == null ? 0 : mc.getMpMax();
        state.enemyMp = state.enemyMpMax;
        state.enemyAttack = mc.getAttack() == null ? 10 : mc.getAttack();
        state.enemyDefense = mc.getDefense() == null ? 5 : mc.getDefense();
        state.nextActionId = 1L;
        state.turnNumber = 1;
        state.currentActorId = playerId;
        state.returnPosX = lock.returnPosX();
        state.returnPosY = lock.returnPosY();
        state.returnPosZ = lock.returnPosZ();
        state.unitCount = resolveLineupUnitCount(req.getLineupId(), req.getLineupUnitIdsCount());
        saveState(state);
        bindActiveBattle(playerId, battleId);

        battleEventPublisher.publishBattleStarted(
                playerId, battleId, lock.sceneId(), enemyEntityId, lock.monsterTemplateId());

        BattleEntityInfo pe = buildEntityInfo(playerId, state.playerName, state.playerLevel,
                state.playerHp, state.playerHpMax, state.playerMp, state.playerMpMax,
                state.playerAttack, state.playerDefense);
        BattleEntityInfo ee = buildEntityInfo(enemyEntityId, state.enemyName, state.enemyLevel,
                state.enemyHp, state.enemyHpMax, state.enemyMp, state.enemyMpMax,
                state.enemyAttack, state.enemyDefense);
        List<BattleEntityInfo> units = expandLineupUnits(playerId, state);

        notifyEnterEncounter(playerId, state);

        return startRsp(RetCode.OK, new StartPayload(
                battleId, ee, pe, lock.sceneId(), units, List.of(ee),
                turnInfoOf(state), enemyEntityId,
                state.returnPosX, state.returnPosY, state.returnPosZ));
    }

    /**
     * 挑战关卡开战：不查场景怪，按 challengeId 生成合成敌人并写入 Redis 战斗状态。
     *
     * @return retcode + battleId（失败时 battleId=0）
     */
    public ChallengeBattleStart startChallengeBattle(long playerId, int lineupId, int challengeId, int battleType) {
        if (playerId <= 0) {
            return new ChallengeBattleStart(RetCode.PLAYER_NOT_SELECTED, 0L);
        }
        int resolvedLineup = lineupId > 0 ? lineupId : 1;
        if (challengeId <= 0) {
            return new ChallengeBattleStart(RetCode.CHALLENGE_NOT_FOUND, 0L);
        }
        var playerOpt = playerRepository.findById(playerId);
        if (playerOpt.isEmpty()) {
            return new ChallengeBattleStart(RetCode.PLAYER_NOT_FOUND, 0L);
        }
        Player player = playerOpt.get();
        String activeKey = REDIS_ACTIVE + playerId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(activeKey))) {
            return new ChallengeBattleStart(RetCode.BATTLE_ALREADY_ACTIVE, 0L);
        }

        ChallengeConfigDocument cfg = challengeConfigLoader.requireOrFallback(challengeId);
        MonsterConfig mc = resolveChallengeMonster(cfg, challengeId);
        int sceneId = 0;
        long enemyEntityId = 900_000L + Integer.toUnsignedLong(challengeId);
        int[] ps = playerCombatStats(player.getLevel() == null ? 1 : player.getLevel());
        long battleId = battleIdSeq.incrementAndGet();
        var state = battleSceneFactory.createState(
                battleId,
                sceneId,
                playerId,
                Integer.toUnsignedLong(resolvedLineup),
                enemyEntityId,
                mc,
                player,
                mc.getName(),
                mc.getLevel() == null ? 1 : mc.getLevel(),
                mc.getExpReward() == null ? 0 : mc.getExpReward(),
                battleType <= 0 ? 2 : battleType
        );
        state.playerHpMax = ps[0];
        state.playerHp = ps[0];
        state.playerMpMax = ps[1];
        state.playerMp = ps[1];
        state.playerAttack = ps[2];
        state.playerDefense = ps[3];
        state.enemyHpMax = mc.getHpMax() == null ? 100 : mc.getHpMax();
        state.enemyHp = state.enemyHpMax;
        state.enemyMpMax = mc.getMpMax() == null ? 0 : mc.getMpMax();
        state.enemyMp = state.enemyMpMax;
        state.enemyAttack = mc.getAttack() == null ? 10 : mc.getAttack();
        state.enemyDefense = mc.getDefense() == null ? 5 : mc.getDefense();
        state.nextActionId = 1L;
        state.turnNumber = 1;
        state.currentActorId = playerId;
        state.unitCount = resolveLineupUnitCount(resolvedLineup, 0);
        state.challengeId = challengeId;
        saveState(state);
        bindActiveBattle(playerId, battleId);
        battleEventPublisher.publishBattleStarted(playerId, battleId, sceneId, enemyEntityId, mc.getId());
        return new ChallengeBattleStart(RetCode.OK, battleId);
    }

    /**
     * 肉鸽房间开战：复用挑战开战通道（sceneId=0），并回写 battleId。
     */
    public ChallengeBattleStart startRogueBattle(long playerId, int lineupId, int rogueId, int roomId, int floor) {
        int challengeLikeId = 10_000 + Math.max(1, rogueId) * 100 + Math.max(1, roomId) + floor;
        return startChallengeBattle(playerId, lineupId > 0 ? lineupId : 1, challengeLikeId, 3);
    }

    /**
     * 挑战结算时强制清理战斗（不校验 HP/奖励），避免占位战斗残留。
     */
    public void forceEndChallengeBattle(long playerId, long battleId) {
        if (playerId <= 0 || battleId <= 0) {
            return;
        }
        BattleRuntimeState state = loadState(battleId);
        if (state != null && state.playerId == playerId) {
            deleteState(battleId);
        }
        String bound = stringRedisTemplate.opsForValue().get(REDIS_ACTIVE + playerId);
        if (bound != null && bound.equals(String.valueOf(battleId))) {
            unbindActiveBattle(playerId);
        }
    }

    private MonsterConfig resolveChallengeMonster(ChallengeConfigDocument cfg, int challengeId) {
        ChallengeConfigDocument.WaveMonster wave = cfg.firstWaveOrNull();
        if (wave != null && wave.getMonsterTemplateId() > 0) {
            MonsterConfig base = configQueryService.findMonsterById(wave.getMonsterTemplateId());
            if (base != null) {
                MonsterConfig mc = new MonsterConfig();
                mc.setId(base.getId());
                mc.setName(cfg.getName() != null ? cfg.getName() : base.getName());
                mc.setModelId(base.getModelId());
                int level = (base.getLevel() == null ? 1 : base.getLevel()) + wave.getLevelBonus();
                mc.setLevel(level);
                int hp = base.getHpMax() == null ? 100 : base.getHpMax();
                int atk = base.getAttack() == null ? 10 : base.getAttack();
                mc.setHpMax((int) Math.max(1, Math.round(hp * wave.getHpMul())));
                mc.setMpMax(base.getMpMax() == null ? 0 : base.getMpMax());
                mc.setAttack((int) Math.max(1, Math.round(atk * wave.getAtkMul())));
                mc.setDefense(base.getDefense() == null ? 5 : base.getDefense());
                mc.setExpReward(base.getExpReward() == null ? 0 : base.getExpReward());
                mc.setDescription(cfg.getDescription());
                return mc;
            }
        }
        return syntheticChallengeMonster(challengeId);
    }

    private static MonsterConfig syntheticChallengeMonster(int challengeId) {
        MonsterConfig mc = new MonsterConfig();
        mc.setId(800_000 + challengeId);
        mc.setName("Challenge-" + challengeId);
        mc.setModelId(challengeId);
        int level = Math.max(1, 10 + (challengeId % 20));
        mc.setLevel(level);
        mc.setHpMax(200 + level * 40);
        mc.setMpMax(level * 5);
        mc.setAttack(30 + level * 4);
        mc.setDefense(10 + level * 2);
        mc.setExpReward(50 + level * 10);
        mc.setDescription("challenge synthetic");
        return mc;
    }

    /** 挑战开战结果。 */
    public record ChallengeBattleStart(int retcode, long battleId) {
    }

    /**
     * 处理「战斗行动」（攻击/技能/道具）。
     *
     * @param playerId 玩家 ID
     * @param req      战斗行动请求
     * @return 协议响应消息
     */
    public ProtocolMessage handleBattleAction(long playerId, BattleActionCsReq req) { // 处理战斗行动
        if (playerId <= 0) { // 玩家 ID 无效
            return actionRsp(RetCode.PLAYER_NOT_SELECTED, req.getBattleId(), 0, 0, 0); // 未选角
        }
        if (antiCheatService.isBanned(playerId)) {
            return actionRsp(RetCode.BATTLE_CHEAT_REJECTED, req.getBattleId(), 0, 0, 0);
        }
        BattleRuntimeState state = loadState(req.getBattleId()); // 从 Redis 加载战斗状态
        if (state == null) { // 战斗不存在或已过期
            return actionRsp(RetCode.BATTLE_NOT_FOUND, req.getBattleId(), 0, 0, 0); // 战斗未找到
        }
        if (state.playerId != playerId) { // 非本场战斗的拥有者
            return actionRsp(RetCode.BATTLE_NOT_FOUND, req.getBattleId(), 0, 0, 0); // 防越权，同样返回未找到
        }
        if (state.ended) { // 战斗已结束不能再操作
            return actionRsp(RetCode.BATTLE_ALREADY_ENDED, req.getBattleId(), 0, 0, 0); // 已结束
        }
        long now = System.currentTimeMillis(); // 服务端当前毫秒时间戳
        if (Math.abs(now - req.getTimestamp()) > TIMESTAMP_WINDOW_MS) { // 客户端时间与服务器差过大
            return actionRsp(RetCode.BATTLE_INVALID_ACTION, req.getBattleId(), 0, 0, 0); // 视为非法行动
        }

        int actionType = req.getActionType(); // 读取行动类型
        if (actionType < ACTION_NORMAL || actionType > ACTION_ITEM) { // 不在 1~3 合法范围
            return actionRsp(RetCode.BATTLE_INVALID_ACTION, req.getBattleId(), 0, 0, 0); // 非法行动类型
        }

        String behaviorKind = actionType == ACTION_SKILL ? "skill" : (actionType == ACTION_ITEM ? "item" : "click");
        AntiCheatService.CheckResult behavior = antiCheatService.checkBehaviorAnomaly(
                playerId, behaviorKind, 1.0, now);
        if (behavior.verdict() != AntiCheatService.Verdict.OK) {
            return actionRsp(RetCode.BATTLE_CHEAT_REJECTED, req.getBattleId(), 0, 0, 0);
        }

        long targetId = req.getTargetEntityId(); // 目标实体 ID
        int damageDealt = 0; // 本次玩家造成的伤害（累计到响应）
        int healDone = 0; // 本次治疗量
        if (actionType == ACTION_ITEM) { // 使用道具只能对自己
            if (targetId != playerId) { // 目标不是自己
                return actionRsp(RetCode.BATTLE_TARGET_INVALID, req.getBattleId(), 0, 0, 0); // 目标无效
            }
        } else if (actionType == ACTION_NORMAL || actionType == ACTION_SKILL) { // 攻击类只能打敌人
            if (targetId != state.enemyEntityId) { // 目标不是敌人
                return actionRsp(RetCode.BATTLE_TARGET_INVALID, req.getBattleId(), 0, 0, 0); // 目标无效
            }
        }

        long actionId = state.nextActionId++;
        state.currentActorId = playerId;
        ReactionResult reactionResult = ReactionResult.noReaction(0);
        if (actionType == ACTION_ITEM) {
            healDone = battlePolicy.computeHeal(actionType, req.getItemId());
            state.playerHp = Math.min(state.playerHpMax, state.playerHp + healDone);
        } else {
            if (actionType == ACTION_SKILL && req.getSkillId() > 0) {
                SkillConfig skill = configQueryService.findSkillById(req.getSkillId());
                if (skill != null) {
                    int manaCost = skill.getManaCost() == null ? 0 : skill.getManaCost();
                    if (state.playerMp < manaCost) {
                        return actionRsp(RetCode.BATTLE_INVALID_ACTION, req.getBattleId(), 0, 0, 0);
                    }
                    state.playerMp = Math.max(0, state.playerMp - manaCost);
                }
            }
            int baseDamage = battlePolicy.computeDamage(
                    state.playerAttack, state.enemyDefense, actionType, req.getSkillId());
            ElementType applied = ElementType.fromCode(req.getElementType());
            reactionResult = elementReactionEngine.resolve(
                    state.battleId, state.enemyEntityId, applied, baseDamage,
                    req.getClientPredictedDamage(), req.getClientPredictedReaction());
            damageDealt = reactionResult.finalDamage();
            AntiCheatService.CheckResult damageCheck = antiCheatService.checkDamage(
                    playerId, Math.max(1, baseDamage), damageDealt);
            if (damageCheck.verdict() != AntiCheatService.Verdict.OK) {
                return actionRsp(RetCode.BATTLE_CHEAT_REJECTED, req.getBattleId(), 0, 0, 0);
            }
            state.enemyHp = Math.max(0, state.enemyHp - damageDealt);
            if (actionType == ACTION_SKILL && req.getSkillId() > 0) {
                battleStatsCollector.recordSkillCast(playerId, req.getSkillId());
            }
        }

        if (state.enemyHp > 0) {
            state.currentActorId = state.enemyEntityId;
            int monsterDamage = battlePolicy.computeDamage(state.enemyAttack, state.playerDefense, ACTION_NORMAL, 0);
            state.playerHp = Math.max(0, state.playerHp - monsterDamage);
        }

        if (state.enemyHp <= 0) {
            state.ended = true;
        } else if (state.playerHp <= 0) {
            state.ended = true;
        } else {
            state.turnNumber = Math.max(1, state.turnNumber) + 1;
            state.currentActorId = playerId;
        }

        saveState(state);
        refreshActiveTtl(playerId, state.battleId);

        var syncBuilder = BattleSyncScNotify.newBuilder()
                .setBattleId(state.battleId)
                .setSyncType(SYNC_ATTR)
                .setTurnInfo(turnInfoOf(state))
                .addEntityUpdates(
                        EntityUpdate.newBuilder()
                        .setEntityId(playerId)
                        .setHp(state.playerHp)
                        .setMp(state.playerMp))
                .addEntityUpdates(
                        EntityUpdate.newBuilder()
                        .setEntityId(state.enemyEntityId)
                        .setHp(state.enemyHp)
                        .setMp(state.enemyMp));
        if (state.ended) {
            syncBuilder.setSyncType(SYNC_BATTLE_END);
        }
        playerNotificationPort.send(playerId, MessageId.BATTLE_SYNC_SC_NOTIFY, syncBuilder.build().toByteArray());

        return actionRsp(RetCode.OK, state.battleId, actionId, damageDealt, healDone, reactionResult);
    }

    /**
     * 处理「战斗结束/结算」请求。
     *
     * @param playerId 玩家 ID
     * @param req      战斗结束请求
     * @return 协议响应消息
     */
    public ProtocolMessage handleBattleEnd(long playerId, BattleEndCsReq req) { // 处理战斗结算
        if (playerId <= 0) { // 玩家 ID 无效
            return endRsp(RetCode.PLAYER_NOT_SELECTED, req.getBattleId(), 0, null, null); // 未选角
        }
        BattleRuntimeState state = loadState(req.getBattleId()); // 加载战斗状态用于校验结果
        if (state == null) { // 战斗不存在
            return endRsp(RetCode.BATTLE_NOT_FOUND, req.getBattleId(), 0, null, null); // 未找到
        }
        if (state.playerId != playerId) { // 校验归属
            return endRsp(RetCode.BATTLE_NOT_FOUND, req.getBattleId(), 0, null, null); // 非本人战斗
        }

        int clientResult = req.getResult(); // 客户端声称的结果：0 败 1 胜 2 平局/逃跑
        if (!validateResult(state, clientResult)) { // 与服务端状态比对，防作弊
            return endRsp(RetCode.BATTLE_RESULT_MISMATCH, req.getBattleId(), 0, null, null); // 结果不一致
        }

        int exp = 0; // 经验奖励
        var currency = new HashMap<Integer, Integer>(); // 货币奖励
        List<ItemReward> items = List.of(); // 道具奖励

        if (clientResult == 1) {
            exp = state.expReward;
            currency.put(1, 500);
            items = List.of(
                    ItemReward.newBuilder()
                    .setItemId(1001)
                    .setCount(1)
                    .build());
            if (state.sceneId > 0) {
                battleScenePort.removeMonsterFromScene(playerId, state.enemyEntityId);
            }
            final int expReward = exp;
            if (expReward > 0) {
                playerRepository.findById(playerId).ifPresent(p -> playerProgressPort.addExp(p, expReward));
            }
        } else if (state.sceneId > 0) {
            // 失败/平局：释放遭遇锁定，怪物回世界可见
            battleScenePort.releaseMonsterFromCombat(playerId, state.enemyEntityId);
        }

        battleEventPublisher.publishBattleEnded(playerId, state.battleId, clientResult, exp, req.getDuration());
        BattleEndProjectionNotifier projection = battleEndProjectionNotifier.getIfAvailable();
        if (projection != null) {
            projection.notifyBattleEnded(playerId, state.battleId, clientResult);
        }
        battleStatsCollector.recordEnded(
                playerId, state.battleId, clientResult, req.getDuration(),
                state.monsterTemplateId, state.playerLevel, exp);
        TLogEventPublisher tlog = tLogEventPublisher == null ? null : tLogEventPublisher.getIfAvailable();
        if (tlog != null) {
            tlog.emit("battle_end", playerId, java.util.Map.of(
                    "battleId", state.battleId,
                    "result", clientResult,
                    "exp", exp,
                    "duration", req.getDuration()));
        }
        notifyExitEncounter(playerId, state, clientResult);
        elementReactionEngine.clearBattle(state.battleId);
        deleteState(state.battleId);
        unbindActiveBattle(playerId);
        RogueService rogueService = rogueServiceProvider.getIfAvailable();
        if (rogueService != null) {
            rogueService.onBattleSettled(playerId, state.battleId, clientResult);
        }
        var currencyMap = new HashMap<Integer, Integer>();
        currencyMap.putAll(currency);

        return endRsp(RetCode.OK, state.battleId, exp, currencyMap, items);
    }

    /**
     * 校验客户端上报结果是否与服务器状态一致。
     *
     * @param state        战斗运行时状态
     * @param clientResult 客户端结果码
     * @return 是否一致
     */
    private static boolean validateResult(BattleRuntimeState state, int clientResult) { // 校验结果一致性
        return switch (clientResult) { // Java switch 表达式
            case 0 -> state.playerHp <= 0; // 失败：玩家应已死
            case 1 -> state.enemyHp <= 0; // 胜利：敌人应已死
            case 2 -> state.playerHp > 0 && state.enemyHp > 0; // 平局/未分胜负：双方都还有血
            default -> false; // 其他结果码非法
        };
    }

    /**
     * 每次行动后延长「进行中战斗」键的生存时间。
     *
     * @param playerId 玩家 ID
     * @param battleId 战斗 ID
     */
    private void refreshActiveTtl(long playerId, long battleId) { // 刷新活跃键 TTL
        bindActiveBattle(playerId, battleId); // 重写并续期，同时确保索引存在
    }

    private void bindActiveBattle(long playerId, long battleId) {
        stringRedisTemplate.opsForValue().set(REDIS_ACTIVE + playerId, String.valueOf(battleId), STATE_TTL);
        stringRedisTemplate.opsForSet().add(REDIS_ACTIVE_INDEX, String.valueOf(playerId));
    }

    private void unbindActiveBattle(long playerId) {
        stringRedisTemplate.delete(REDIS_ACTIVE + playerId);
        stringRedisTemplate.opsForSet().remove(REDIS_ACTIVE_INDEX, String.valueOf(playerId));
    }

    /**
     * 将战斗运行时状态序列化存入 Redis。
     *
     * @param state 战斗状态
     */
    private void saveState(BattleRuntimeState state) { // 持久化战斗状态
        try { // 捕获序列化异常
            String json = objectMapper.writeValueAsString(state); // 对象转 JSON 字符串
            stringRedisTemplate.opsForValue().set(REDIS_STATE + state.battleId, json, STATE_TTL); // 键：battle:state:{id}
        } catch (Exception e) { // 写入失败
            throw new IllegalStateException("战斗状态写入 Redis 失败", e); // 抛出运行时异常
        }
    }

    /**
     * 按战斗 ID 从 Redis 反序列化状态。
     *
     * @param battleId 战斗 ID
     * @return 战斗状态，不存在或解析失败返回 null
     */
    private BattleRuntimeState loadState(long battleId) { // 加载战斗状态
        try { // 捕获反序列化异常
            String json = stringRedisTemplate.opsForValue().get(REDIS_STATE + battleId); // 读取 JSON
            if (json == null) { // 键不存在或已过期
                return null;
            }
            return objectMapper.readValue(json, BattleRuntimeState.class); // JSON 转对象
        } catch (Exception e) { // 解析失败
            return null; // 视为无此战斗（不向外抛错，由上层返回 BATTLE_NOT_FOUND）
        }
    }

    /**
     * 战斗结束后删除状态键。
     *
     * @param battleId 战斗 ID
     */
    private void deleteState(long battleId) { // 删除战斗状态
        stringRedisTemplate.delete(REDIS_STATE + battleId); // 删除 Redis 键
    }

    /**
     * 将内部数值组装为协议 Protobuf 实体信息。
     *
     * @return BattleEntityInfo 消息
     */
    private static BattleEntityInfo buildEntityInfo( // 构建实体信息
            long entityId, String name, int level,
            int hp, int hpMax, int mp, int mpMax, int atk, int def) {
        return BattleEntityInfo.newBuilder() // Protobuf 建造者模式
                .setEntityId(entityId) // 实体 ID
                .setName(name == null ? "" : name) // 空名用空串
                .setLevel(level) // 等级
                .setHp(hp) // 当前 HP
                .setHpMax(hpMax) // 最大 HP
                .setMp(mp) // 当前 MP
                .setMpMax(mpMax) // 最大 MP
                .setAttack(atk) // 攻击力
                .setDefense(def) // 防御力
                .build(); // 生成不可变消息对象
    }

    /**
     * 根据等级计算 [最大HP, 最大MP, 攻击, 防御]。
     *
     * @param level 玩家等级
     * @return 四维属性数组
     */
    private static int[] playerCombatStats(int level) { // 计算玩家战斗属性
        int lv = Math.max(1, level); // 等级至少为 1
        return new int[]{ // 返回四维数组
                100 + lv * 50, // 最大 HP
                lv * 10, // 最大 MP
                50 + lv * 5, // 攻击力
                20 + lv * 2 // 防御力
        };
    }

    private record StartPayload(
            long battleId,
            BattleEntityInfo enemy,
            BattleEntityInfo player,
            int sceneId,
            List<BattleEntityInfo> playerUnits,
            List<BattleEntityInfo> enemyUnits,
            TurnInfo turnInfo,
            long sceneEntityId,
            float returnPosX,
            float returnPosY,
            float returnPosZ) {
        static StartPayload empty() {
            return new StartPayload(0, null, null, 0, List.of(), List.of(), null, 0, 0, 0, 0);
        }
    }

    private static ProtocolMessage startRsp(int code, StartPayload payload) {
        var b = BattleStartScRsp.newBuilder()
                .setRetcode(code)
                .setServerTime(System.currentTimeMillis() / 1000);
        if (code == RetCode.OK && payload != null) {
            b.setBattleId(payload.battleId())
                    .setSceneId(payload.sceneId())
                    .setEnemyInfo(payload.enemy())
                    .setPlayerInfo(payload.player())
                    .setSceneEntityId(payload.sceneEntityId())
                    .setReturnPosX(payload.returnPosX())
                    .setReturnPosY(payload.returnPosY())
                    .setReturnPosZ(payload.returnPosZ());
            if (payload.playerUnits() != null) {
                b.addAllPlayerUnits(payload.playerUnits());
            }
            if (payload.enemyUnits() != null) {
                b.addAllEnemyUnits(payload.enemyUnits());
            }
            if (payload.turnInfo() != null) {
                b.setTurnInfo(payload.turnInfo());
            }
        }
        return new ProtocolMessage(MessageId.BATTLE_START_SC_RSP, b.build().toByteArray());
    }

    private static TurnInfo turnInfoOf(BattleRuntimeState state) {
        return TurnInfo.newBuilder()
                .setCurrentActorId(state.ended ? 0L : state.currentActorId)
                .setTurnNumber(Math.max(1, state.turnNumber))
                .setTimeLimit(TURN_TIME_LIMIT_SEC)
                .build();
    }

    private static int resolveLineupUnitCount(long lineupId, int explicitUnitCount) {
        if (explicitUnitCount > 0) {
            return Math.min(4, explicitUnitCount);
        }
        int fromLineup = (int) Math.max(1, Math.min(4, lineupId));
        return fromLineup;
    }

    private List<BattleEntityInfo> expandLineupUnits(long playerId, BattleRuntimeState state) {
        List<BattleEntityInfo> units = new ArrayList<>();
        units.add(buildEntityInfo(playerId, state.playerName, state.playerLevel,
                state.playerHp, state.playerHpMax, state.playerMp, state.playerMpMax,
                state.playerAttack, state.playerDefense));
        for (int i = 1; i < state.unitCount; i++) {
            long unitId = playerId * 10 + i;
            float scale = 1f - i * 0.08f;
            units.add(buildEntityInfo(
                    unitId,
                    state.playerName + "-U" + (i + 1),
                    Math.max(1, state.playerLevel - i),
                    Math.max(1, (int) (state.playerHpMax * scale)),
                    Math.max(1, (int) (state.playerHpMax * scale)),
                    Math.max(0, (int) (state.playerMpMax * scale)),
                    Math.max(0, (int) (state.playerMpMax * scale)),
                    Math.max(1, (int) (state.playerAttack * scale)),
                    Math.max(1, (int) (state.playerDefense * scale))));
        }
        return units;
    }

    private void notifyEnterEncounter(long playerId, BattleRuntimeState state) {
        if (state.sceneId <= 0) {
            return;
        }
        EnterEncounterScNotify notify = EnterEncounterScNotify.newBuilder()
                .setBattleId(state.battleId)
                .setSceneId(state.sceneId)
                .setSceneEntityId(state.enemyEntityId)
                .setReturnPosX(state.returnPosX)
                .setReturnPosY(state.returnPosY)
                .setReturnPosZ(state.returnPosZ)
                .build();
        playerNotificationPort.send(playerId, MessageId.ENTER_ENCOUNTER_SC_NOTIFY, notify.toByteArray());
    }

    private void notifyExitEncounter(long playerId, BattleRuntimeState state, int result) {
        if (state.sceneId <= 0) {
            return;
        }
        ExitEncounterScNotify notify = ExitEncounterScNotify.newBuilder()
                .setBattleId(state.battleId)
                .setSceneId(state.sceneId)
                .setResult(result)
                .setReturnPosX(state.returnPosX)
                .setReturnPosY(state.returnPosY)
                .setReturnPosZ(state.returnPosZ)
                .build();
        playerNotificationPort.send(playerId, MessageId.EXIT_ENCOUNTER_SC_NOTIFY, notify.toByteArray());
    }

    /**
     * 组装回合行动结果。
     *
     * @return 协议消息
     */
    private static ProtocolMessage actionRsp(int code, long battleId, long actionId, int damage, int heal) {
        return actionRsp(code, battleId, actionId, damage, heal, ReactionResult.noReaction(damage));
    }

    private static ProtocolMessage actionRsp(int code, long battleId, long actionId, int damage, int heal,
                                             ReactionResult reaction) { // 行动响应（含元素反应与回滚标记）
        ReactionResult r = reaction == null ? ReactionResult.noReaction(damage) : reaction;
        var b = BattleActionScRsp.newBuilder()
                .setRetcode(code)
                .setBattleId(battleId)
                .setActionId(actionId); // 基础字段
        if (damage > 0) { // 有伤害才设置字段（Protobuf 可选语义）
            b.setDamage(damage); // 设置伤害
        }
        if (heal > 0) { // 有治疗才设置
            b.setHeal(heal); // 设置治疗
        }
        b.setReactionType(r.reaction().getCode())
                .setAuraElement(r.remainingAura().getCode())
                .setRollback(r.rollback())
                .setFinalDamage(Math.max(0, r.finalDamage() > 0 ? r.finalDamage() : damage));
        return new ProtocolMessage(MessageId.BATTLE_ACTION_SC_RSP, b.build().toByteArray()); // 供客户端播放伤害/治疗表现
    }

    /**
     * 组装战斗结算：经验、货币与掉落道具。
     *
     * @return 协议消息
     */
    private static ProtocolMessage endRsp( // 结束战斗响应
            int code, long battleId, int exp, java.util.Map<Integer, Integer> currency, java.util.List<ItemReward> items) {
        var b = BattleEndScRsp.newBuilder()
                .setRetcode(code)
                .setBattleId(battleId); // 基础字段
        if (code == RetCode.OK) { // 成功才带奖励
            if (exp > 0) { // 有经验
                b.setPlayerExp(exp); // 设置经验
            }
            if (currency != null) { // 有货币
                b.putAllCurrencyRewards(currency); // 批量放入货币 map
            }
            if (items != null) { // 有道具
                b.addAllItemRewards(items); // 道具列表
            }
        }
        return new ProtocolMessage(MessageId.BATTLE_END_SC_RSP, b.build().toByteArray()); // 供背包与等级系统入账
    }

    /**
     * Redis 持久化的战斗运行时状态（Jackson 序列化）。
     */
    public static class BattleRuntimeState { // 单场战斗在 Redis 中的完整快照

        /** 战斗唯一 ID */
        public long battleId; // 战斗 ID
        /** 参与玩家 ID */
        public long playerId; // 玩家 ID
        /** 所在场景 ID */
        public int sceneId; // 场景 ID
        /** 阵容 ID */
        public long lineupId; // 阵容 ID
        /** 战斗类型（PVE/PVP 等，由协议定义） */
        public int battleType; // 战斗类型
        /** 场景中敌人实体实例 ID */
        public long enemyEntityId; // 敌人实体 ID
        /** 怪物配置模板 ID */
        public int monsterTemplateId; // 怪物模板 ID
        /** 胜利可获得的经验（来自配置） */
        public int expReward; // 经验奖励
        /** 玩家显示名 */
        public String playerName; // 玩家名称
        /** 敌人显示名 */
        public String enemyName; // 敌人名称
        /** 玩家等级 */
        public int playerLevel; // 玩家等级
        /** 敌人等级 */
        public int enemyLevel; // 敌人等级
        /** 玩家当前生命值 */
        public int playerHp; // 玩家当前 HP
        /** 玩家最大生命值 */
        public int playerHpMax; // 玩家最大 HP
        /** 玩家当前法力值 */
        public int playerMp; // 玩家当前 MP
        /** 玩家最大法力值 */
        public int playerMpMax; // 玩家最大 MP
        /** 玩家攻击力 */
        public int playerAttack; // 玩家攻击
        /** 玩家防御力 */
        public int playerDefense; // 玩家防御
        /** 敌人当前生命值 */
        public int enemyHp; // 敌人当前 HP
        /** 敌人最大生命值 */
        public int enemyHpMax; // 敌人最大 HP
        /** 敌人当前法力值 */
        public int enemyMp; // 敌人当前 MP
        /** 敌人最大法力值 */
        public int enemyMpMax; // 敌人最大 MP
        /** 敌人攻击力 */
        public int enemyAttack; // 敌人攻击
        /** 敌人防御力 */
        public int enemyDefense; // 敌人防御
        /** 下一条客户端行动序号（递增） */
        public long nextActionId;
        /** 是否已在服务端判定结束（尚未调用 end 结算） */
        public boolean ended;
        /** 回合编号（TurnInfo.turn_number） */
        public int turnNumber;
        /** 当前行动方 entityId */
        public long currentActorId;
        /** 编队展开单位数 1–4 */
        public int unitCount = 1;
        /** 回世界坐标 */
        public float returnPosX;
        public float returnPosY;
        public float returnPosZ;
        /** 挑战关卡 ID（非挑战为 0） */
        public int challengeId;
    }
}
